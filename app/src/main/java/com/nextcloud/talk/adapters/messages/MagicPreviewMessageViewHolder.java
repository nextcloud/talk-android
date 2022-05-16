/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.components.filebrowser.webdav.ReadFilesystemOperation;
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DrawableUtils;
import com.nextcloud.talk.utils.FileViewerUtils;
import com.stfalcon.chatkit.messages.MessageHolders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.emoji.widget.EmojiTextView;
import autodagger.AutoInjector;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;

import static com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback.REPLYABLE_VIEW_TAG;

@AutoInjector(NextcloudTalkApplication.class)
public abstract class MagicPreviewMessageViewHolder extends MessageHolders.IncomingImageMessageViewHolder<ChatMessage> {

    private static final String TAG = "PreviewMsgViewHolder";
    public static final String KEY_CONTACT_NAME = "contact-name";
    public static final String KEY_CONTACT_PHOTO = "contact-photo";
    public static final String KEY_MIMETYPE = "mimetype";
    public static final String KEY_ID = "id";
    public static final String KEY_PATH = "path";
    public static final String ACTOR_TYPE_BOTS = "bots";
    public static final String ACTOR_ID_CHANGELOG = "changelog";
    public static final String KEY_NAME = "name";

    @Inject
    Context context;

    @Inject
    OkHttpClient okHttpClient;

    ProgressBar progressBar;

    ReactionsInsideMessageBinding reactionsBinding;

    FileViewerUtils fileViewerUtils;

    View clickView;

    ReactionsInterface reactionsInterface;
    PreviewMessageInterface previewMessageInterface;

    public MagicPreviewMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBind(ChatMessage message) {
        super.onBind(message);
        if (userAvatar != null) {
            if (message.isGrouped() || message.isOneToOneConversation()) {
                if (message.isOneToOneConversation()) {
                    userAvatar.setVisibility(View.GONE);
                } else {
                    userAvatar.setVisibility(View.INVISIBLE);
                }
            } else {
                userAvatar.setVisibility(View.VISIBLE);
                userAvatar.setOnClickListener(v -> {
                    if (payload instanceof ProfileBottomSheet) {
                        ((ProfileBottomSheet) payload).showFor(message.getActorId(), v.getContext());
                    }
                });

                if (ACTOR_TYPE_BOTS.equals(message.getActorType()) && ACTOR_ID_CHANGELOG.equals(message.getActorId())) {
                    if (context != null) {
                        Drawable[] layers = new Drawable[2];
                        layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background);
                        layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground);
                        LayerDrawable layerDrawable = new LayerDrawable(layers);

                        userAvatar.getHierarchy().setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable));
                    }
                }
            }
        }

        progressBar = getProgressBar();
        image = getImage();
        clickView = getImage();
        getMessageText().setVisibility(View.VISIBLE);

        if (message.getCalculateMessageType() == ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {

            fileViewerUtils = new FileViewerUtils(context, message.getActiveUser());

            String fileName = message.getSelectedIndividualHashMap().get(KEY_NAME);
            getMessageText().setText(fileName);
            if (message.getSelectedIndividualHashMap().containsKey(KEY_CONTACT_NAME)) {
                getPreviewContainer().setVisibility(View.GONE);
                getPreviewContactName().setText(message.getSelectedIndividualHashMap().get(KEY_CONTACT_NAME));
                progressBar = getPreviewContactProgressBar();
                getMessageText().setVisibility(View.INVISIBLE);
                clickView = getPreviewContactContainer();
            } else {
                getPreviewContainer().setVisibility(View.VISIBLE);
                getPreviewContactContainer().setVisibility(View.GONE);
            }

            if (message.getSelectedIndividualHashMap().containsKey(KEY_CONTACT_PHOTO)) {
                image = getPreviewContactPhoto();
                Drawable drawable = getDrawableFromContactDetails(
                    context,
                    message.getSelectedIndividualHashMap().get(KEY_CONTACT_PHOTO));
                image.getHierarchy().setPlaceholderImage(drawable);
            } else if (message.getSelectedIndividualHashMap().containsKey(KEY_MIMETYPE)) {
                String mimetype = message.getSelectedIndividualHashMap().get(KEY_MIMETYPE);
                int drawableResourceId = DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType(mimetype);
                Drawable drawable = ContextCompat.getDrawable(context, drawableResourceId);
                image.getHierarchy().setPlaceholderImage(drawable);
            } else {
                fetchFileInformation("/" + message.getSelectedIndividualHashMap().get(KEY_PATH),
                                     message.getActiveUser());
            }

            if (message.getActiveUser() != null &&
                message.getActiveUser().getUsername() != null &&
                message.getActiveUser().getBaseUrl() != null) {
                clickView.setOnClickListener(v ->
                    fileViewerUtils.openFile(
                        message,
                        new FileViewerUtils.ProgressUi(progressBar, getMessageText(), image)
                    )
                );

                clickView.setOnLongClickListener(l -> {
                    onMessageViewLongClick(message);
                    return true;
                });
            } else {
                Log.e(TAG, "failed to set click listener because activeUser, username or baseUrl were null");
            }

            fileViewerUtils.resumeToUpdateViewsByProgress(
                Objects.requireNonNull(message.getSelectedIndividualHashMap().get(MagicPreviewMessageViewHolder.KEY_NAME)),
                Objects.requireNonNull(message.getSelectedIndividualHashMap().get(MagicPreviewMessageViewHolder.KEY_ID)),
                message.getSelectedIndividualHashMap().get(MagicPreviewMessageViewHolder.KEY_MIMETYPE),
                new FileViewerUtils.ProgressUi(progressBar, getMessageText(), image));

        } else if (message.getCalculateMessageType() == ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE) {
            getMessageText().setText("GIPHY");
            DisplayUtils.setClickableString("GIPHY", "https://giphy.com", getMessageText());
        } else if (message.getCalculateMessageType() == ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE) {
            getMessageText().setText("Tenor");
            DisplayUtils.setClickableString("Tenor", "https://tenor.com", getMessageText());
        } else {
            if (message.getMessageType().equals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE)) {
                clickView.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getImageUrl()));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                });
            } else {
                clickView.setOnClickListener(null);
            }
            getMessageText().setText("");
        }

        itemView.setTag(REPLYABLE_VIEW_TAG, message.getReplyable());

        reactionsBinding = getReactionsBinding();
        new Reaction().showReactions(message, reactionsBinding, getMessageText().getContext(), true);
        reactionsBinding.reactionsEmojiWrapper.setOnClickListener(l -> {
            reactionsInterface.onClickReactions(message);
        });
        reactionsBinding.reactionsEmojiWrapper.setOnLongClickListener(l -> {
            reactionsInterface.onLongClickReactions(message);
            return true;
        });
    }

    private Drawable getDrawableFromContactDetails(Context context, String base64) {
        Drawable drawable = null;
        if (!base64.equals("")) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                Base64.decode(base64.getBytes(), Base64.DEFAULT));
            drawable = Drawable.createFromResourceStream(context.getResources(),
                                                         null, inputStream, null, null);
            try {
                inputStream.close();
            } catch (IOException e) {
                int drawableResourceId = DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType("text/vcard");
                drawable = ContextCompat.getDrawable(context, drawableResourceId);
            }
        }

        return drawable;
    }

    private void onMessageViewLongClick(ChatMessage message) {
        if (fileViewerUtils.isSupportedForInternalViewer(message.getSelectedIndividualHashMap().get(KEY_MIMETYPE))) {
            previewMessageInterface.onPreviewMessageLongClick(message);
            return;
        }

        Context viewContext;

        if (itemView != null && itemView.getContext() != null) {
            viewContext = itemView.getContext();
        } else {
            viewContext = this.context;
        }

        PopupMenu popupMenu = new PopupMenu(
            new ContextThemeWrapper(viewContext, R.style.appActionBarPopupMenu),
            itemView,
            Gravity.START
        );
        popupMenu.inflate(R.menu.chat_preview_message_menu);

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId()== R.id.openInFiles){
                String keyID = message.getSelectedIndividualHashMap().get(KEY_ID);
                String link = message.getSelectedIndividualHashMap().get("link");
                fileViewerUtils.openFileInFilesApp(link, keyID);
            }
            return true;
        });

        popupMenu.show();
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
                public void onSubscribe(@NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onSuccess(@NonNull ReadFilesystemOperation readFilesystemOperation) {
                    DavResponse davResponse = readFilesystemOperation.readRemotePath();
                    if (davResponse.data != null) {
                        List<BrowserFile> browserFileList = (List<BrowserFile>) davResponse.data;
                        if (!browserFileList.isEmpty()) {
                            new Handler(context.getMainLooper()).post(() -> {
                                int resourceId = DrawableUtils
                                    .INSTANCE
                                    .getDrawableResourceIdForMimeType(browserFileList.get(0).mimeType);
                                Drawable drawable = ContextCompat.getDrawable(context, resourceId);
                                image.getHierarchy().setPlaceholderImage(drawable);
                            });
                        }
                    }
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    Log.e(TAG, "Error reading file information", e);
                }
            });
    }

    public void assignReactionInterface(ReactionsInterface reactionsInterface) {
        this.reactionsInterface = reactionsInterface;
    }

    public void assignPreviewMessageInterface(PreviewMessageInterface previewMessageInterface) {
        this.previewMessageInterface = previewMessageInterface;
    }

    public abstract EmojiTextView getMessageText();

    public abstract ProgressBar getProgressBar();

    public abstract SimpleDraweeView getImage();

    public abstract View getPreviewContainer();

    public abstract View getPreviewContactContainer();

    public abstract SimpleDraweeView getPreviewContactPhoto();

    public abstract EmojiTextView getPreviewContactName();

    public abstract ProgressBar getPreviewContactProgressBar();

    public abstract ReactionsInsideMessageBinding getReactionsBinding();
}
