/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.ItemSharedItemsLoadingBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils

/**
 * Footer showing a progress indicator below the shared items while [isLoading] is set.
 */
class SharedItemsLoadingAdapter(private val viewThemeUtils: ViewThemeUtils) :
    RecyclerView.Adapter<SharedItemsLoadingAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSharedItemsLoadingBinding) : RecyclerView.ViewHolder(binding.root)

    var isLoading: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) notifyItemInserted(0) else notifyItemRemoved(0)
            }
        }

    override fun getItemCount(): Int = if (isLoading) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSharedItemsLoadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        viewThemeUtils.material.colorProgressBar(binding.sharedItemsLoadingProgress)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit
}
