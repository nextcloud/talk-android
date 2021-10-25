/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.interfaces.SelectionInterface;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DateUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DrawableUtils;

import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

@AutoInjector(NextcloudTalkApplication.class)
public class BrowserFileItem extends AbstractFlexibleItem<BrowserFileItem.ViewHolder> implements IFilterable<String> {
    @Inject
    Context context;
    private BrowserFile browserFile;
    private UserEntity activeUser;
    private SelectionInterface selectionInterface;
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
    public ViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ViewHolder(view, adapter);

    }

    private boolean isSelected() {
        return selected;
    }

    private void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ViewHolder holder, int position, List<Object> payloads) {
        holder.fileIconImageView.setController(null);
        if (!browserFile.isAllowedToReShare() || browserFile.isEncrypted()) {
            holder.itemView.setEnabled(false);
            holder.itemView.setAlpha(0.38f);
        } else {
            holder.itemView.setEnabled(true);
            holder.itemView.setAlpha(1.0f);
        }

        if (browserFile.isEncrypted()) {
            holder.fileEncryptedImageView.setVisibility(View.VISIBLE);

        } else {
            holder.fileEncryptedImageView.setVisibility(View.GONE);
        }

        if (browserFile.isFavorite()) {
            holder.fileFavoriteImageView.setVisibility(View.VISIBLE);
        } else {
            holder.fileFavoriteImageView.setVisibility(View.GONE);
        }

        if (selectionInterface.shouldOnlySelectOneImageFile()) {
            if (browserFile.isFile && browserFile.mimeType.startsWith("image/")) {
                holder.selectFileCheckbox.setVisibility(View.VISIBLE);
            } else {
                holder.selectFileCheckbox.setVisibility(View.GONE);
            }
        } else {
            holder.selectFileCheckbox.setVisibility(View.VISIBLE);
        }

        if (context != null) {
            holder
                .fileIconImageView
                .getHierarchy()
                .setPlaceholderImage(
                    AppCompatResources.getDrawable(
                        context, DrawableUtils.INSTANCE.getDrawableResourceIdForMimeType(browserFile.getMimeType())));
        }

        if (browserFile.isHasPreview()) {
            String path = ApiUtils.getUrlForFilePreviewWithRemotePath(activeUser.getBaseUrl(),
                    browserFile.getPath(),
                    context.getResources().getDimensionPixelSize(R.dimen.small_item_height));

            if (path.length() > 0) {
                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(path, null))
                        .build();
                holder.fileIconImageView.setController(draweeController);
            }
        }

        holder.filenameTextView.setText(browserFile.getDisplayName());
        holder.fileModifiedTextView.setText(String.format(context.getString(R.string.nc_last_modified),
                Formatter.formatShortFileSize(context, browserFile.getSize()),
                DateUtils.INSTANCE.getLocalDateTimeStringFromTimestamp(browserFile.getModifiedTimestamp())));
        setSelected(selectionInterface.isPathSelected(browserFile.getPath()));
        holder.selectFileCheckbox.setChecked(isSelected());

        if (!browserFile.isEncrypted()) {
            holder.selectFileCheckbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!browserFile.isAllowedToReShare()) {
                        ((CheckBox) v).setChecked(false);
                        Toast.makeText(context, context.getResources().getString(R.string.nc_file_browser_reshare_forbidden),
                                Toast.LENGTH_LONG).show();
                    } else if (((CheckBox) v).isChecked() != isSelected()) {
                        setSelected(((CheckBox) v).isChecked());
                        selectionInterface.toggleBrowserItemSelection(browserFile.getPath());
                    }
                }
            });
        }

        holder.filenameTextView.setSelected(true);
        holder.fileModifiedTextView.setSelected(true);
    }

    @Override
    public boolean filter(String constraint) {
        return false;
    }

    static class ViewHolder extends FlexibleViewHolder {

        @BindView(R.id.file_icon)
        public SimpleDraweeView fileIconImageView;
        @BindView(R.id.file_modified_info)
        public TextView fileModifiedTextView;
        @BindView(R.id.filename_text_view)
        public TextView filenameTextView;
        @BindView(R.id.select_file_checkbox)
        public CheckBox selectFileCheckbox;
        @BindView(R.id.fileEncryptedImageView)
        public ImageView fileEncryptedImageView;
        @BindView(R.id.fileFavoriteImageView)
        public ImageView fileFavoriteImageView;

        ViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
