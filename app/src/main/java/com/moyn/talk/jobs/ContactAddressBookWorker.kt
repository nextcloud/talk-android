/*
 * Nextcloud Talk application
 *  
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.moyn.talk.jobs

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Controller
import com.google.gson.Gson
import com.moyn.talk.BuildConfig
import com.moyn.talk.R
import com.moyn.talk.api.NcApi
import com.moyn.talk.application.NextcloudTalkApplication
import com.moyn.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.moyn.talk.models.json.search.ContactsByNumberOverall
import com.moyn.talk.utils.ApiUtils
import com.moyn.talk.utils.database.user.UserUtils
import com.moyn.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContactAddressBookWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var accountName: String
    private lateinit var accountType: String

    override fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val currentUser = userUtils.currentUser

        accountName = context.getString(R.string.nc_app_product_name)
        accountType = BuildConfig.APPLICATION_ID

        if (currentUser == null) {
            Log.e(javaClass.simpleName, "No current user!")
            return Result.failure()
        }

        val deleteAll = inputData.getBoolean(DELETE_ALL, false)
        if (deleteAll) {
            deleteAllLinkedAccounts()
            return Result.success()
        }

        // Check if run already at the date
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!force) {
            if (System.currentTimeMillis() - appPreferences.getPhoneBookIntegrationLastRun(0L) < 24 * 60 * 60 * 1000) {
                Log.d(TAG, "Already run within last 24h")
                return Result.success()
            }
        }

        if (AccountManager.get(context).getAccountsByType(accountType).isEmpty()) {
            AccountManager.get(context).addAccountExplicitly(Account(accountName, accountType), "", null)
        } else {
            Log.d(TAG, "Account already exists")
        }

        val deviceContactsWithNumbers = collectContactsWithPhoneNumbersFromDevice()

        if (deviceContactsWithNumbers.isNotEmpty()) {
            val currentLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0].country

            val map = mutableMapOf<String, Any>()
            map["location"] = currentLocale
            map["search"] = deviceContactsWithNumbers

            val json = Gson().toJson(map)

            ncApi.searchContactsByPhoneNumber(
                ApiUtils.getCredentials(currentUser.username, currentUser.token),
                ApiUtils.getUrlForSearchByNumber(currentUser.baseUrl),
                json.toRequestBody("application/json".toMediaTypeOrNull())
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ContactsByNumberOverall> {
                    override fun onComplete() {
                    }

                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(foundContacts: ContactsByNumberOverall) {
                        val contactsWithAssociatedPhoneNumbers = foundContacts.ocs.map
                        deleteLinkedAccounts(contactsWithAssociatedPhoneNumbers)
                        createLinkedAccounts(contactsWithAssociatedPhoneNumbers)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(javaClass.simpleName, "Failed to searchContactsByPhoneNumber", e)
                    }
                })
        }

        // store timestamp 
        appPreferences.setPhoneBookIntegrationLastRun(System.currentTimeMillis())

        return Result.success()
    }

    private fun collectContactsWithPhoneNumbersFromDevice(): MutableMap<String, List<String>> {
        val deviceContactsWithNumbers: MutableMap<String, List<String>> = mutableMapOf()

        val contactCursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        if (contactCursor != null) {
            if (contactCursor.count > 0) {
                contactCursor.moveToFirst()
                for (i in 0 until contactCursor.count) {
                    val id = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts._ID))
                    val lookup =
                        contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY))
                    deviceContactsWithNumbers[lookup] = getPhoneNumbersFromDeviceContact(id)
                    contactCursor.moveToNext()
                }
            }
            contactCursor.close()
        }
        Log.d(TAG, "collected contacts with phonenumbers: " + deviceContactsWithNumbers.size)
        return deviceContactsWithNumbers
    }

    private fun deleteLinkedAccounts(contactsWithAssociatedPhoneNumbers: Map<String, String>?) {
        Log.d(TAG, "deleteLinkedAccount")
        fun deleteLinkedAccount(id: String) {
            val rawContactUri = ContactsContract.RawContacts.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                .build()
            val count = context.contentResolver.delete(
                rawContactUri,
                ContactsContract.RawContacts.CONTACT_ID + " " + "LIKE \"" + id + "\"",
                null
            )
            Log.d(TAG, "deleted $count linked accounts for id $id")
        }

        val rawContactUri = ContactsContract.Data.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .appendQueryParameter(
                ContactsContract.Data.MIMETYPE,
                "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat"
            )
            .build()

        val rawContactsCursor = context.contentResolver.query(
            rawContactUri,
            null,
            null,
            null,
            null
        )

        if (rawContactsCursor != null) {
            if (rawContactsCursor.count > 0) {
                while (rawContactsCursor.moveToNext()) {
                    val lookupKey =
                        rawContactsCursor.getString(rawContactsCursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY))
                    val contactId =
                        rawContactsCursor.getString(rawContactsCursor.getColumnIndex(ContactsContract.Data.CONTACT_ID))

                    if (contactsWithAssociatedPhoneNumbers == null || !contactsWithAssociatedPhoneNumbers.containsKey(
                            lookupKey
                        )
                    ) {
                        deleteLinkedAccount(contactId)
                    }
                }
            } else {
                Log.d(TAG, "no contacts with linked Talk Accounts found. Nothing to delete...")
            }
            rawContactsCursor.close()
        }
    }

    private fun createLinkedAccounts(contactsWithAssociatedPhoneNumbers: Map<String, String>?) {
        fun hasLinkedAccount(id: String): Boolean {
            var hasLinkedAccount = false
            val where =
                ContactsContract.Data.MIMETYPE +
                    " = ? AND " +
                    ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID +
                    " = ?"
            val params = arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, id)

            val rawContactUri = ContactsContract.Data.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(
                    ContactsContract.Data.MIMETYPE,
                    "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat"
                )
                .build()

            val rawContactsCursor = context.contentResolver.query(
                rawContactUri,
                null,
                where,
                params,
                null
            )

            if (rawContactsCursor != null) {
                if (rawContactsCursor.count > 0) {
                    hasLinkedAccount = true
                    Log.d(TAG, "contact with id $id already has a linked account")
                } else {
                    hasLinkedAccount = false
                }
                rawContactsCursor.close()
            }
            return hasLinkedAccount
        }

        fun createLinkedAccount(lookupKey: String, cloudId: String) {
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            val lookupContactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
            val contactCursor = context.contentResolver.query(
                lookupContactUri,
                null,
                null,
                null,
                null
            )

            if (contactCursor != null) {
                if (contactCursor.count > 0) {
                    contactCursor.moveToFirst()

                    val id = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts._ID))
                    if (hasLinkedAccount(id)) {
                        return
                    }

                    val numbers = getPhoneNumbersFromDeviceContact(id)
                    val displayName = getDisplayNameFromDeviceContact(id)

                    if (displayName == null) {
                        return
                    }

                    val ops = ArrayList<ContentProviderOperation>()
                    val rawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().build()
                    val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon().build()

                    ops.add(
                        ContentProviderOperation
                            .newInsert(rawContactsUri)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                            .withValue(
                                ContactsContract.RawContacts.AGGREGATION_MODE,
                                ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT
                            )
                            .withValue(ContactsContract.RawContacts.SYNC2, cloudId)
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation
                            .newInsert(dataUri)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, numbers[0])
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation
                            .newInsert(dataUri)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                            )
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                            .build()
                    )
                    ops.add(
                        ContentProviderOperation
                            .newInsert(dataUri)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat"
                            )
                            .withValue(ContactsContract.Data.DATA1, cloudId)
                            .withValue(
                                ContactsContract.Data.DATA2,
                                String.format(
                                    context.resources.getString(
                                        R.string.nc_phone_book_integration_chat_via
                                    ),
                                    accountName
                                )
                            )
                            .build()
                    )

                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (e: OperationApplicationException) {
                        Log.e(javaClass.simpleName, "", e)
                    } catch (e: RemoteException) {
                        Log.e(javaClass.simpleName, "", e)
                    }

                    Log.d(
                        TAG,
                        "added new entry for contact $displayName (cloudId: $cloudId | lookupKey: $lookupKey" +
                            " | id: $id)"
                    )
                }
                contactCursor.close()
            }
        }

        if (contactsWithAssociatedPhoneNumbers != null && contactsWithAssociatedPhoneNumbers.isNotEmpty()) {
            for (contact in contactsWithAssociatedPhoneNumbers) {
                val lookupKey = contact.key
                val cloudId = contact.value
                createLinkedAccount(lookupKey, cloudId)
            }
        } else {
            Log.d(TAG, "no contacts with linked Talk Accounts found. No linked accounts created.")
        }
    }

    private fun getDisplayNameFromDeviceContact(id: String?): String? {
        var displayName: String? = null
        val whereName =
            ContactsContract.Data.MIMETYPE +
                " = ? AND " +
                ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID +
                " = ?"
        val whereNameParams = arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, id)
        val nameCursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            whereName,
            whereNameParams,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
        )
        if (nameCursor != null) {
            while (nameCursor.moveToNext()) {
                displayName =
                    nameCursor.getString(
                        nameCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                    )
            }
            nameCursor.close()
        }
        return displayName
    }

    private fun getPhoneNumbersFromDeviceContact(id: String?): MutableList<String> {
        val numbers = mutableListOf<String>()
        val phonesNumbersCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            ContactsContract.Data.CONTACT_ID + " = " + id,
            null,
            null
        )

        if (phonesNumbersCursor != null) {
            while (phonesNumbersCursor.moveToNext()) {
                numbers.add(
                    phonesNumbersCursor.getString(
                        phonesNumbersCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    )
                )
            }
            phonesNumbersCursor.close()
        }
        if (numbers.size > 0) {
            Log.d(TAG, "Found ${numbers.size} phone numbers for contact with id $id")
        }
        return numbers
    }

    fun deleteAllLinkedAccounts() {
        val rawContactUri = ContactsContract.RawContacts.CONTENT_URI
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .build()
        context.contentResolver.delete(rawContactUri, null, null)
        Log.d(TAG, "deleted all linked accounts")
    }

    companion object {
        const val TAG = "ContactAddressBook"
        const val REQUEST_PERMISSION = 231
        const val KEY_FORCE = "KEY_FORCE"
        const val DELETE_ALL = "DELETE_ALL"

        fun run(context: Context) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
            ) {
                WorkManager
                    .getInstance()
                    .enqueue(
                        OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                            .setInputData(Data.Builder().putBoolean(KEY_FORCE, false).build())
                            .build()
                    )
            }
        }

        fun checkPermission(controller: Controller, context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                controller.requestPermissions(
                    arrayOf(
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_CONTACTS
                    ),
                    REQUEST_PERMISSION
                )
                return false
            } else {
                WorkManager
                    .getInstance()
                    .enqueue(
                        OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                            .setInputData(Data.Builder().putBoolean(KEY_FORCE, true).build())
                            .build()
                    )
                return true
            }
        }

        fun deleteAll() {
            WorkManager
                .getInstance()
                .enqueue(
                    OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                        .setInputData(Data.Builder().putBoolean(DELETE_ALL, true).build())
                        .build()
                )
        }
    }
}
