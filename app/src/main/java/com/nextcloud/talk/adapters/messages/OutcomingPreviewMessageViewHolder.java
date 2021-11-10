/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters.messages;

import android.view.View;
import android.widget.ProgressBar;

import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.databinding.ItemCustomOutcomingPreviewMessageBinding;

import androidx.emoji.widget.EmojiTextView;

public class OutcomingPreviewMessageViewHolder extends MagicPreviewMessageViewHolder {
    private final ItemCustomOutcomingPreviewMessageBinding binding;

    public OutcomingPreviewMessageViewHolder(View itemView) {
        super(itemView, null);
        binding = ItemCustomOutcomingPreviewMessageBinding.bind(itemView);
    }

    @Override
    public EmojiTextView getMessageText() {
        return binding.messageText;
    }

    @Override
    public ProgressBar getProgressBar() {
        return binding.progressBar;
    }

    @Override
    public SimpleDraweeView getImage() {
        return binding.image;
    }

    @Override
    public View getPreviewContainer() {
        return binding.previewContainer;
    }

    @Override
    public View getPreviewContactContainer() {
        return binding.contactContainer;
    }

    @Override
    public SimpleDraweeView getPreviewContactPhoto() {
        return binding.contactPhoto;
    }

    @Override
    public EmojiTextView getPreviewContactName() {
        return binding.contactName;
    }

    @Override
    public ProgressBar getPreviewContactProgressBar() {
        return binding.contactProgressBar;
    }
}
