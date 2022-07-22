/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.polls.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogPollLoadingBinding

@AutoInjector(NextcloudTalkApplication::class)
class PollLoadingFragment : Fragment() {

    private lateinit var binding: DialogPollLoadingBinding

    var fragmentHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        fragmentHeight = arguments?.getInt(KEY_FRAGMENT_HEIGHT)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogPollLoadingBinding.inflate(inflater, container, false)
        binding.root.layoutParams.height = fragmentHeight
        return binding.root
    }

    companion object {
        private val TAG = PollLoadingFragment::class.java.simpleName
        private const val KEY_FRAGMENT_HEIGHT = "keyFragmentHeight"

        @JvmStatic
        fun newInstance(
            fragmentHeight: Int
        ): PollLoadingFragment {

            val args = bundleOf(
                KEY_FRAGMENT_HEIGHT to fragmentHeight,
            )

            val fragment = PollLoadingFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
