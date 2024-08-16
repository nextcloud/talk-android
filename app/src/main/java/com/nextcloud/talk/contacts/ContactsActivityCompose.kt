/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationcreation.ConversationCreationActivity
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.openconversations.ListOpenConversationsActivity
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContactsActivityCompose : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var contactsViewModel: ContactsViewModel

    @SuppressLint("UnrememberedMutableState")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        contactsViewModel = ViewModelProvider(this, viewModelFactory)[ContactsViewModel::class.java]
        val isAddParticipants = intent.getBooleanExtra("isAddParticipants", false)
        contactsViewModel.updateIsAddParticipants(isAddParticipants)
        if (isAddParticipants) {
            contactsViewModel.updateShareTypes(
                listOf(
                    ShareType.Group.shareType,
                    ShareType.Email.shareType,
                    ShareType.Circle.shareType
                )
            )
            contactsViewModel.getContactsFromSearchParams()
        }
        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val uiState = contactsViewModel.contactsViewState.collectAsState()
            MaterialTheme(
                colorScheme = colorScheme
            ) {
                val context = LocalContext.current
                Scaffold(
                    topBar = {
                        AppBar(
                            title = stringResource(R.string.nc_app_product_name),
                            context = context,
                            contactsViewModel = contactsViewModel
                        )
                    },
                    content = {
                        Column(Modifier.padding(it)) {
                            ConversationCreationOptions(context = context, contactsViewModel = contactsViewModel)
                            ContactsList(
                                contactsUiState = uiState.value,
                                contactsViewModel = contactsViewModel,
                                context = context
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ContactsList(contactsUiState: ContactsUiState, contactsViewModel: ContactsViewModel, context: Context) {
    when (contactsUiState) {
        is ContactsUiState.None -> {
        }
        is ContactsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ContactsUiState.Success -> {
            val contacts = contactsUiState.contacts
            Log.d(CompanionClass.TAG, "Contacts:$contacts")
            if (contacts != null) {
                ContactsItem(contacts, contactsViewModel, context)
            }
        }
        is ContactsUiState.Error -> {
            val errorMessage = contactsUiState.message
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = Color.Red)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsItem(contacts: List<AutocompleteUser>, contactsViewModel: ContactsViewModel, context: Context) {
    val groupedContacts: Map<String, List<AutocompleteUser>> = contacts.groupBy { contact ->
        (
            if (contact.source == "users") {
                contact.label?.first()?.uppercase()
            } else {
                contact.source?.replaceFirstChar { actorType ->
                    actorType.uppercase()
                }
            }
            ).toString()
    }
    LazyColumn(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        groupedContacts.forEach { (initial, contactsForInitial) ->
            stickyHeader {
                Column {
                    Surface(Modifier.fillParentMaxWidth()) {
                        Header(initial)
                    }
                    HorizontalDivider(thickness = 0.1.dp, color = Color.Black)
                }
            }
            items(contactsForInitial) { contact ->
                ContactItemRow(contact = contact, contactsViewModel = contactsViewModel, context = context)
                Log.d(CompanionClass.TAG, "Contacts:$contact")
            }
        }
    }
}

@Composable
fun Header(header: String) {
    Text(
        text = header,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(start = 60.dp),
        color = Color.Blue,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ContactItemRow(contact: AutocompleteUser, contactsViewModel: ContactsViewModel, context: Context) {
    val roomUiState by contactsViewModel.roomViewState.collectAsState()
    val isAddParticipants = contactsViewModel.isAddParticipantsView.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isAddParticipants.value) {
                    contactsViewModel.createRoom(
                        CompanionClass.ROOM_TYPE_ONE_ONE,
                        contact.source!!,
                        contact.id!!,
                        null
                    )
                } else {
                    if (isSelected) {
                        selectedContacts.remove(contact)
                    } else {
                        selectedContacts.add(contact)
                    }
                    contactsViewModel.updateSelectedParticipants(selectedContacts)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUri = contact.id?.let { contactsViewModel.getImageUri(it, true) }
        val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
        val loadedImage = loadImage(imageUri, context, errorPlaceholderImage)
        AsyncImage(
            model = loadedImage,
            contentDescription = stringResource(R.string.user_avatar),
            modifier = Modifier.size(width = 45.dp, height = 45.dp)
        )
        Text(modifier = Modifier.padding(16.dp), text = contact.label!!)
        if (isAddParticipants.value) {
            if (isSelected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_check_circle),
                    contentDescription = "Selected",
                    tint = Color.Blue,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
    when (roomUiState) {
        is RoomUiState.Success -> {
            val conversation = (roomUiState as RoomUiState.Success).conversation
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation?.token)
            val chatIntent = Intent(context, ChatActivity::class.java)
            chatIntent.putExtras(bundle)
            chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(chatIntent)
        }
        is RoomUiState.Error -> {
            val errorMessage = (roomUiState as RoomUiState.Error).message
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $errorMessage", color = Color.Red)
            }
        }
        is RoomUiState.None -> {}
    }
}

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, context: Context, contactsViewModel: ContactsViewModel) {
    val searchQuery by contactsViewModel.searchQuery.collectAsState()
    val searchState = contactsViewModel.searchState.collectAsState()
    val isAddParticipants = contactsViewModel.isAddParticipantsView.collectAsState()

    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = {
                (context as? Activity)?.finish()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
            }
        },
        actions = {
            IconButton(onClick = {
                contactsViewModel.updateSearchState(true)
            }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_icon))
            }
            if (isAddParticipants.value) {
                Text(
                    text = stringResource(id = R.string.nc_contacts_done),
                    modifier = Modifier.clickable {
                        val selectedParticipants: List<AutocompleteUser> = contactsViewModel.selectedParticipantsList
                        val intent = Intent(context, ConversationCreationActivity::class.java)
                        intent.putParcelableArrayListExtra(
                            "selectedParticipants",
                            selectedParticipants as ArrayList<Parcelable>
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
    )
    if (searchState.value) {
        Row {
            DisplaySearch(
                text = searchQuery,
                onTextChange = { searchQuery ->
                    contactsViewModel.updateSearchQuery(query = searchQuery)
                    contactsViewModel.getContactsFromSearchParams()
                },
                contactsViewModel = contactsViewModel
            )
        }
    }
}

@Composable
fun ConversationCreationOptions(context: Context, contactsViewModel: ContactsViewModel) {
    val isAddParticipants by contactsViewModel.isAddParticipantsView.collectAsState()
    if (!isAddParticipants) {
        Column {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    .clickable {
                        val intent = Intent(context, ConversationCreationActivity::class.java)
                        context.startActivity(intent)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_chat_bubble_outline_24),
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .padding(8.dp),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = stringResource(R.string.nc_create_new_conversation),
                    maxLines = 1,
                    fontSize = 16.sp
                )
            }
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .clickable {
                        val intent = Intent(context, ListOpenConversationsActivity::class.java)
                        context.startActivity(intent)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .padding(8.dp),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = stringResource(R.string.nc_join_open_conversations),
                    fontSize = 16.sp
                )
            }
        }
    }
}

class CompanionClass {
    companion object {
        internal val TAG = ContactsActivityCompose::class.simpleName
        internal const val ROOM_TYPE_ONE_ONE = "1"
    }
}
