/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters.messages;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;

import com.google.common.util.concurrent.ListenableFuture;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.FullScreenImageActivity;
import com.nextcloud.talk.activities.FullScreenMediaActivity;
import com.nextcloud.talk.activities.FullScreenTextViewerActivity;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.components.filebrowser.webdav.ReadFilesystemOperation;
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.utils.AccountUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DrawableUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.stfalcon.chatkit.messages.MessageHolders;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import androidx.core.content.ContextCompat;
import androidx.emoji.widget.EmojiTextView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.jetbrains.annotations.NotNull;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicPreviewMessageViewHolder extends MessageHolders.IncomingImageMessageViewHolder<ChatMessage> {

    private static String TAG = "MagicPreviewMessageViewHolder";

    @BindView(R.id.messageText)
    EmojiTextView messageText;

    View progressBar;

    @Inject
    Context context;

    @Inject
    OkHttpClient okHttpClient;

    public MagicPreviewMessageViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        progressBar = itemView.findViewById(R.id.progress_bar);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);
        if (userAvatar != null) {
            if (message.isGrouped || message.isOneToOneConversation) {
                if (message.isOneToOneConversation) {
                    userAvatar.setVisibility(View.GONE);
                } else {
                    userAvatar.setVisibility(View.INVISIBLE);
                }
            } else {
                userAvatar.setVisibility(View.VISIBLE);

                if ("bots".equals(message.actorType) && "changelog".equals(message.actorId)) {
                    Drawable[] layers = new Drawable[2];
                    layers[0] = context.getDrawable(R.drawable.ic_launcher_background);
                    layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground);
                    LayerDrawable layerDrawable = new LayerDrawable(layers);

                    userAvatar.getHierarchy().setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable));
                }
            }
        }

        if (message.getMessageType() == ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
            String fileName = message.getSelectedIndividualHashMap().get("name");
            messageText.setText(fileName);
            if (message.getSelectedIndividualHashMap().containsKey("mimetype")) {
                String mimetype = message.getSelectedIndividualHashMap().get("mimetype");
                int drawableResourceId = DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType(mimetype);
                Drawable drawable = ContextCompat.getDrawable(context, drawableResourceId);
                image.getHierarchy().setPlaceholderImage(drawable);
            } else {
                fetchFileInformation("/" + message.getSelectedIndividualHashMap().get("path"), message.activeUser);
            }

            String accountString =
                    message.activeUser.getUsername() + "@" + message.activeUser.getBaseUrl().replace("https://", "").replace("http://", "");

            image.setOnClickListener(v -> {
                String mimetype = message.getSelectedIndividualHashMap().get("mimetype");
                if (isSupportedMimetype(mimetype)) {
                    openOrDownloadFile(message);
                } else {
                    openFileInFilesApp(message, accountString);
                }
            });

            image.setOnLongClickListener(l -> {
                onMessageViewLongClick(message, accountString);
                return true;
            });

            // check if download worker is already running
            String fileId = message.getSelectedIndividualHashMap().get("id");
            ListenableFuture<List<WorkInfo>> workers = WorkManager.getInstance(context).getWorkInfosByTag(fileId);

            try {
                for (WorkInfo workInfo : workers.get()) {
                    if (workInfo.getState() == WorkInfo.State.RUNNING || workInfo.getState() == WorkInfo.State.ENQUEUED) {
                        progressBar.setVisibility(View.VISIBLE);

                        String mimetype = message.getSelectedIndividualHashMap().get("mimetype");

                        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workInfo.getId()).observeForever(info -> {
                            updateViewsByProgress(fileName, mimetype, info);
                        });
                    }
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error when checking if worker already exists", e);
            }

        } else if (message.getMessageType() == ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE) {
            messageText.setText("GIPHY");
            DisplayUtils.setClickableString("GIPHY", "https://giphy.com", messageText);
        } else if (message.getMessageType() == ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE) {
            messageText.setText("Tenor");
            DisplayUtils.setClickableString("Tenor", "https://tenor.com", messageText);
        } else {
            if (message.getMessageType().equals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE)) {
                image.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getImageUrl()));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                });
            } else {
                image.setOnClickListener(null);
            }
            messageText.setText("");
        }
    }

    public boolean isSupportedMimetype(String mimetype){
        switch (mimetype) {
            case "image/png":
            case "image/jpeg":
            case "image/gif":
            case "audio/mpeg":
            case "audio/wav":
            case "audio/ogg":
            case "video/mp4":
            case "video/quicktime":
            case "video/ogg":
            case "text/markdown":
            case "text/plain":
                return true;
            default:
                return false;
        }
    }

    private void openOrDownloadFile(ChatMessage message) {
        String filename = message.getSelectedIndividualHashMap().get("name");
        String mimetype = message.getSelectedIndividualHashMap().get("mimetype");
        File file = new File(context.getCacheDir(), filename);
        if (file.exists()) {
            openFile(filename, mimetype);
        } else {
            String size = message.getSelectedIndividualHashMap().get("size");

            if (size == null) {
                size = "-1";
            }
            Integer fileSize = Integer.valueOf(size);

            String fileId = message.getSelectedIndividualHashMap().get("id");
            String path = message.getSelectedIndividualHashMap().get("path");
            downloadFileToCache(
                    message.activeUser.getBaseUrl(),
                    message.activeUser.getUserId(),
                    message.activeUser.getAttachmentFolder(),
                    filename,
                    path,
                    mimetype,
                    fileSize,
                    fileId
            );
        }
    }

    private void openFile(String filename, String mimetype) {
        switch (mimetype) {
            case "audio/mpeg":
            case "audio/wav":
            case "audio/ogg":
            case "video/mp4":
            case "video/quicktime":
            case "video/ogg":
                openMediaView(filename, mimetype);
                break;
            case "image/png":
            case "image/jpeg":
            case "image/gif":
                openImageView(filename, mimetype);
                break;
            case "text/markdown":
            case "text/plain":
                openTextView(filename, mimetype);
                break;
            default:
                Log.w(TAG, "no method defined for mimetype: " + mimetype);
        }
    }

    private void openImageView(String filename, String mimetype) {
        Intent fullScreenImageIntent = new Intent(context, FullScreenImageActivity.class);
        fullScreenImageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenImageIntent.putExtra("FILE_NAME", filename);
        fullScreenImageIntent.putExtra("IS_GIF", isGif(mimetype));
        context.startActivity(fullScreenImageIntent);
    }

    private void openFileInFilesApp(ChatMessage message, String accountString) {
        if (AccountUtils.INSTANCE.canWeOpenFilesApp(context, accountString)) {
            Intent filesAppIntent = new Intent(Intent.ACTION_VIEW, null);
            final ComponentName componentName = new ComponentName(context.getString(R.string.nc_import_accounts_from), "com.owncloud.android.ui.activity.FileDisplayActivity");
            filesAppIntent.setComponent(componentName);
            filesAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            filesAppIntent.setPackage(context.getString(R.string.nc_import_accounts_from));
            filesAppIntent.putExtra(BundleKeys.INSTANCE.getKEY_ACCOUNT(), accountString);
            filesAppIntent.putExtra(BundleKeys.INSTANCE.getKEY_FILE_ID(), message.getSelectedIndividualHashMap().get("id"));
            context.startActivity(filesAppIntent);
        } else {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getSelectedIndividualHashMap().get("link")));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(browserIntent);
        }
    }

    private void onMessageViewLongClick(ChatMessage message, String accountString) {
        if (isSupportedMimetype(message.getSelectedIndividualHashMap().get("mimetype"))) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(this.context, itemView, Gravity.START);
        popupMenu.inflate(R.menu.chat_preview_message_menu);

        popupMenu.setOnMenuItemClickListener(item -> {
            openFileInFilesApp(message, accountString);
            return true;
        });

        popupMenu.show();
    }

    @SuppressLint("LongLogTag")
    private void downloadFileToCache(String baseUrl,
                                     String userId,
                                     String attachmentFolder,
                                     String fileName,
                                     String path,
                                     String mimetype,
                                     Integer size,
                                     String fileId) {

        // check if download worker is already running
        ListenableFuture<List<WorkInfo>> workers = WorkManager.getInstance(context).getWorkInfosByTag(fileId);

        try {
            for (WorkInfo workInfo : workers.get()) {
                if (workInfo.getState() == WorkInfo.State.RUNNING || workInfo.getState() == WorkInfo.State.ENQUEUED) {
                    Log.d(TAG, "Download worker for " + fileId + " is already running or " +
                            "scheduled");
                    return;
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error when checking if worker already exsists", e);
        }

        Data data;
        OneTimeWorkRequest downloadWorker;

        data = new Data.Builder()
                .putString(DownloadFileToCacheWorker.KEY_BASE_URL, baseUrl)
                .putString(DownloadFileToCacheWorker.KEY_USER_ID, userId)
                .putString(DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER, attachmentFolder)
                .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, fileName)
                .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, path)
                .putInt(DownloadFileToCacheWorker.KEY_FILE_SIZE, size)
                .build();

        downloadWorker = new OneTimeWorkRequest.Builder(DownloadFileToCacheWorker.class)
                .setInputData(data)
                .addTag(fileId)
                .build();

        WorkManager.getInstance().enqueue(downloadWorker);

        progressBar.setVisibility(View.VISIBLE);

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorker.getId()).observeForever(workInfo -> {
            updateViewsByProgress(fileName, mimetype, workInfo);
        });
    }

    private void updateViewsByProgress(String fileName, String mimetype, WorkInfo workInfo) {
        switch (workInfo.getState()) {
            case RUNNING:
                int progress = workInfo.getProgress().getInt(DownloadFileToCacheWorker.PROGRESS, -1);
                if (progress > -1) {
                    messageText.setText(String.format(context.getResources().getString(R.string.filename_progress), fileName, progress));
                }
                break;

            case SUCCEEDED:
                if (image.isShown()) {
                    openFile(fileName, mimetype);
                } else {
                    Log.d(TAG, "image " + fileName + " was downloaded but it's not opened (view is not shown)");
                }
                messageText.setText(fileName);
                progressBar.setVisibility(View.GONE);
                break;

            case FAILED:
                messageText.setText(fileName);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                // do nothing
                break;
        }
    }

    private void openMediaView(String filename, String mimetype) {
        Intent fullScreenMediaIntent = new Intent(context, FullScreenMediaActivity.class);
        fullScreenMediaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenMediaIntent.putExtra("FILE_NAME", filename);
        fullScreenMediaIntent.putExtra("AUDIO_ONLY", isAudioOnly(mimetype));
        context.startActivity(fullScreenMediaIntent);
    }

    private void openTextView(String filename, String mimetype) {
        Intent fullScreenTextViewerIntent = new Intent(context, FullScreenTextViewerActivity.class);
        fullScreenTextViewerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenTextViewerIntent.putExtra("FILE_NAME", filename);
        fullScreenTextViewerIntent.putExtra("IS_MARKDOWN", isMarkdown(mimetype));
        context.startActivity(fullScreenTextViewerIntent);
    }

    private boolean isGif(String mimetype) {
        return ("image/gif").equals(mimetype);
    }

    private boolean isMarkdown(String mimetype) {
        return ("text/markdown").equals(mimetype);
    }

    private boolean isAudioOnly(String mimetype) {
        return mimetype.startsWith("audio");
    }

    private void fetchFileInformation(String url, UserEntity activeUser) {
        Single.fromCallable(new Callable<ReadFilesystemOperation>() {
            @Override
            public ReadFilesystemOperation call() {
                return new ReadFilesystemOperation(okHttpClient, activeUser, url, 0);
            }
        }).observeOn(Schedulers.io())
                .subscribe(new SingleObserver<ReadFilesystemOperation>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(@NotNull ReadFilesystemOperation readFilesystemOperation) {
                        DavResponse davResponse = readFilesystemOperation.readRemotePath();
                        if (davResponse.data != null) {
                            List<BrowserFile> browserFileList = (List<BrowserFile>) davResponse.data;
                            if (!browserFileList.isEmpty()) {
                                new Handler(context.getMainLooper()).post(() -> {
                                    int resourceId = DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType(browserFileList.get(0).mimeType);
                                    Drawable drawable = ContextCompat.getDrawable(context, resourceId);
                                    image.getHierarchy().setPlaceholderImage(drawable);
                                });
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                    }
                });

    }
}
