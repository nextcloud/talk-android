/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.contacts.ContactsViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationCreationActivity : BaseActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var contactsViewModel: ContactsViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        contactsViewModel = ViewModelProvider(this, viewModelFactory)[ContactsViewModel::class.java]
        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            MaterialTheme(
                colorScheme = colorScheme
            ) {
                val context = LocalContext.current
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(id = R.string.nc_new_conversation)) },

                            navigationIcon = {
                                IconButton(onClick = {
                                    (context as? Activity)?.finish()
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back_button)
                                    )
                                }
                            }
                        )
                    },
                    content = {
                        Column(Modifier.padding(it)) {
                            UserAvatar()
                            UploadAvatar()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserAvatar()  {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.drawable.ic_circular_group,
            contentDescription = "User Avatar",
            modifier = Modifier
                .size(width = 84.dp, height = 84.dp)
                .padding(top = 16.dp)
        )
    }
}

@Composable
fun UploadAvatar()  {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = {
        }) {
            Icon(painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24), contentDescription = null)
        }

        IconButton(onClick = {
        }) {
            Icon(painter = painterResource(id = R.drawable.ic_folder_multiple_image), contentDescription = null)
        }

        IconButton(onClick = {
        }) {
            Icon(painter = painterResource(id = R.drawable.ic_delete_grey600_24dp), contentDescription = null)
        }
    }
}
