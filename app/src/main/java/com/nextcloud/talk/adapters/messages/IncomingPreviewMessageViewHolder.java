/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Tim Krüger
 * @author Marcel Hibbe
 * Copyright (C) 2022-2023 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021-2022 Tim Krüger <t@timkrueger.me>
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
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.material.card.MaterialCardView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.databinding.ItemCustomIncomingPreviewMessageBinding;
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding;
import com.nextcloud.talk.models.json.chat.ChatMessage;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.emoji2.widget.EmojiTextView;

public class IncomingPreviewMessageViewHolder extends PreviewMessageViewHolder {
    private final ItemCustomIncomingPreviewMessageBinding binding;

    public IncomingPreviewMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        binding = ItemCustomIncomingPreviewMessageBinding.bind(itemView);
    }

    @Override
    public void onBind(@NonNull ChatMessage message) {
        super.onBind(message);

        binding.messageAuthor.setText(message.getActorDisplayName());
        binding.messageText.setTextColor(ContextCompat.getColor(binding.messageText.getContext(),
                                                                R.color.no_emphasis_text));
        binding.messageTime.setTextColor(ContextCompat.getColor(binding.messageText.getContext(),
                                                                R.color.no_emphasis_text));
    }

    @NonNull
    @Override
    public EmojiTextView getMessageText() {
        return binding.messageText;
    }

    @Override
    public ProgressBar getProgressBar() {
        return binding.progressBar;
    }

    @NonNull
    @Override
    public View getPreviewContainer() {
        return binding.previewContainer;
    }

    @NonNull
    @Override
    public MaterialCardView getPreviewContactContainer() {
        return binding.contactContainer;
    }

    @NonNull
    @Override
    public ImageView getPreviewContactPhoto() {
        return binding.contactPhoto;
    }

    @NonNull
    @Override
    public EmojiTextView getPreviewContactName() {
        return binding.contactName;
    }

    @Override
    public ProgressBar getPreviewContactProgressBar() {
        return binding.contactProgressBar;
    }

    @Override
    public ReactionsInsideMessageBinding getReactionsBinding(){ return binding.reactions; }

}
