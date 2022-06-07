/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.components.filebrowser.adapters.items;

import android.content.Context;
import android.text.format.Formatter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.databinding.RvItemBrowserFileBinding;
import com.nextcloud.talk.databinding.RvItemBrowserFileOldBinding;
import com.nextcloud.talk.interfaces.SelectionInterface;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DateUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DrawableUtils;

import java.util.List;

import javax.inject.Inject;

import androidx.appcompat.content.res.AppCompatResources;
import autodagger.AutoInjector;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

@AutoInjector(NextcloudTalkApplication.class)
public class BrowserFileItem extends AbstractFlexibleItem<BrowserFileItem.BrowserFileItemViewHolder> implements IFilterable<String> {
    @Inject
    Context context;
    private final BrowserFile browserFile;
    private final UserEntity activeUser;
    private final SelectionInterface selectionInterface;
    private boolean selected;

    public BrowserFileItem(BrowserFile browserFile, UserEntity activeUser, SelectionInterface selectionInterface) {
        this.browserFile = browserFile;
        this.activeUser = activeUser;
        this.selectionInterface = selectionInterface;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BrowserFileItem) {
            BrowserFileItem inItem = (BrowserFileItem) o;
            return browserFile.getPath().equals(inItem.getModel().getPath());
        }

        return false;
    }

    public BrowserFile getModel() {
        return browserFile;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_browser_file;
    }

    @Override
    public BrowserFileItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new BrowserFileItemViewHolder(view, adapter);
    }

    private boolean isSelected() {
        return selected;
    }

    private void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter,
                               BrowserFileItemViewHolder holder,
                               int position,
                               List<Object> payloads) {
        holder.binding.fileIcon.setController(null);
        if (!browserFile.isAllowedToReShare() || browserFile.isEncrypted()) {
            holder.itemView.setEnabled(false);
            holder.itemView.setAlpha(0.38f);
        } else {
            holder.itemView.setEnabled(true);
            holder.itemView.setAlpha(1.0f);
        }

        if (browserFile.isEncrypted()) {
            holder.binding.fileEncryptedImageView.setVisibility(View.VISIBLE);

        } else {
            holder.binding.fileEncryptedImageView.setVisibility(View.GONE);
        }

        if (browserFile.isFavorite()) {
            holder.binding.fileFavoriteImageView.setVisibility(View.VISIBLE);
        } else {
            holder.binding.fileFavoriteImageView.setVisibility(View.GONE);
        }

        if (selectionInterface.shouldOnlySelectOneImageFile()) {
            if (browserFile.isFile() && browserFile.getMimeType().startsWith("image/")) {
                holder.binding.selectFileCheckbox.setVisibility(View.VISIBLE);
            } else {
                holder.binding.selectFileCheckbox.setVisibility(View.GONE);
            }
        } else {
            holder.binding.selectFileCheckbox.setVisibility(View.VISIBLE);
        }

        if (context != null) {
            holder
                .binding
                .fileIcon
                .getHierarchy()
                .setPlaceholderImage(
                    AppCompatResources.getDrawable(
                        context, DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType(browserFile.getMimeType())));
        }

        if (browserFile.getHasPreview()) {
            String path = ApiUtils.getUrlForFilePreviewWithRemotePath(activeUser.getBaseUrl(),
                    browserFile.getPath(),
                    context.getResources().getDimensionPixelSize(R.dimen.small_item_height));

            if (path.length() > 0) {
                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(path, null))
                        .build();
                holder.binding.fileIcon.setController(draweeController);
            }
        }

        holder.binding.filenameTextView.setText(browserFile.getDisplayName());
        holder.binding.fileModifiedInfo.setText(String.format(context.getString(R.string.nc_last_modified),
                Formatter.formatShortFileSize(context, browserFile.getSize()),
                DateUtils.INSTANCE.getLocalDateTimeStringFromTimestamp(browserFile.getModifiedTimestamp())));
        setSelected(selectionInterface.isPathSelected(browserFile.getPath()));
        holder.binding.selectFileCheckbox.setChecked(isSelected());

        if (!browserFile.isEncrypted()) {
            holder.binding.selectFileCheckbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!browserFile.isAllowedToReShare()) {
                        ((CheckBox) v).setChecked(false);
                        Toast.makeText(
                            context,
                            context.getResources().getString(R.string.nc_file_browser_reshare_forbidden),
                            Toast.LENGTH_LONG)
                            .show();
                    } else if (((CheckBox) v).isChecked() != isSelected()) {
                        setSelected(((CheckBox) v).isChecked());
                        selectionInterface.toggleBrowserItemSelection(browserFile.getPath());
                    }
                }
            });
        }

        holder.binding.filenameTextView.setSelected(true);
        holder.binding.fileModifiedInfo.setSelected(true);
    }

    @Override
    public boolean filter(String constraint) {
        return false;
    }

    static class BrowserFileItemViewHolder extends FlexibleViewHolder {

        RvItemBrowserFileOldBinding binding;

        BrowserFileItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = RvItemBrowserFileOldBinding.bind(view);
        }
    }
}
