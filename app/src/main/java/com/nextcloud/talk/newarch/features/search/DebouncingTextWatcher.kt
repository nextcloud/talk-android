/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.search

import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DebouncingTextWatcher(
        lifecycle: Lifecycle,
        private val onDebouncingTextWatcherChange: (CharSequence?) -> Unit
) : TextWatcher, SearchView.OnQueryTextListener {
    private var debouncePeriod: Long = 500

    private val coroutineScope = lifecycle.coroutineScope

    private var searchJob: Job? = null

    override fun afterTextChanged(s: Editable?) {

    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        textChanged(s)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        // do nothing, we already handle it in onQueryTextChange
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        textChanged(newText)
        return true
    }

    private fun textChanged(charSequence: CharSequence?) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            charSequence.let {
                delay(debouncePeriod)
                onDebouncingTextWatcherChange(charSequence)
            }
        }
    }
}