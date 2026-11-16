/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.ItemSharedItemsLoadStateBinding
import com.nextcloud.talk.shareditems.viewmodels.SharedItemsViewModel.NextPageState
import com.nextcloud.talk.ui.theme.ViewThemeUtils

/**
 * Footer below the shared items that shows a progress indicator while the next page loads, and a retry button
 * calling [onRetry] after it failed to load.
 */
class SharedItemsLoadStateAdapter(private val viewThemeUtils: ViewThemeUtils, private val onRetry: () -> Unit) :
    RecyclerView.Adapter<SharedItemsLoadStateAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSharedItemsLoadStateBinding) : RecyclerView.ViewHolder(binding.root)

    var nextPageState: NextPageState = NextPageState.IDLE
        set(value) {
            if (field == value) return
            val wasShown = field != NextPageState.IDLE
            val isShown = value != NextPageState.IDLE
            field = value
            when {
                wasShown && isShown -> notifyItemChanged(0)
                isShown -> notifyItemInserted(0)
                wasShown -> notifyItemRemoved(0)
            }
        }

    override fun getItemCount(): Int = if (nextPageState == NextPageState.IDLE) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSharedItemsLoadStateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        viewThemeUtils.material.colorProgressBar(binding.sharedItemsLoadStateProgress)
        viewThemeUtils.material.colorMaterialButtonText(binding.sharedItemsLoadStateRetry)
        binding.sharedItemsLoadStateRetry.setOnClickListener { onRetry() }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.sharedItemsLoadStateProgress.isVisible = nextPageState == NextPageState.LOADING
        holder.binding.sharedItemsLoadStateError.isVisible = nextPageState == NextPageState.FAILED
    }
}
