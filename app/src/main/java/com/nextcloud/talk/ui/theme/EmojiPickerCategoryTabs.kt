/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.theme

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.recyclerview.widget.RecyclerView
import androidx.emoji2.emojipicker.R as EmojiPickerR

/**
 * androidx.emoji2.emojipicker.EmojiPickerView tints its selected category tab via the
 * platform `?android:attr/colorAccent`, which this app never wires up to its dynamic
 * per-server branding (that color only exists as a runtime value computed from
 * [com.nextcloud.android.common.ui.theme.MaterialSchemes], not as a theme attribute).
 * This reaches into the picker's header RecyclerView - not public API, but stable
 * since the library's initial release - to retint each category tab directly.
 *
 * EmojiPickerView builds its header/body views asynchronously: it loads the bundled
 * emoji data on a background coroutine and only inflates its real layout afterwards, so
 * the header doesn't exist yet right after construction. This waits for that via a
 * global layout listener, then reapplies tint on every adapter rebind too, since
 * EmojiPickerHeaderAdapter.selectedGroupIndex rebinds the old and new tab on every
 * category change. Safe no-op if the internal view IDs ever change in a future version.
 */
fun themeEmojiPickerCategoryTabs(emojiPickerView: EmojiPickerView, selectedColor: Int, unselectedColor: Int) {
    val tint = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(selectedColor, unselectedColor)
    )

    fun tintTab(tabView: View) {
        tabView.findViewById<ImageView>(EmojiPickerR.id.emoji_picker_header_icon)?.imageTintList = tint
        tabView.findViewById<View>(EmojiPickerR.id.emoji_picker_header_underline)?.backgroundTintList = tint
    }

    fun tintAllTabs(header: RecyclerView) {
        for (i in 0 until header.childCount) {
            tintTab(header.getChildAt(i))
        }
    }

    fun attachTo(header: RecyclerView) {
        tintAllTabs(header)

        header.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = tintTab(view)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
        )

        header.adapter?.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = reapply()
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = reapply()

                private fun reapply() {
                    header.post { tintAllTabs(header) }
                }
            }
        )
    }

    val header = emojiPickerView.findViewById<RecyclerView>(EmojiPickerR.id.emoji_picker_header)
    if (header != null) {
        attachTo(header)
        return
    }

    emojiPickerView.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val lateHeader =
                    emojiPickerView.findViewById<RecyclerView>(EmojiPickerR.id.emoji_picker_header) ?: return
                emojiPickerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                attachTo(lateHeader)
            }
        }
    )
}
