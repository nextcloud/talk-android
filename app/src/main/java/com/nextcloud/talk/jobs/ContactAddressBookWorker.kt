/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.google.gson.Gson
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.search.ContactsByNumberOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ContactUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
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
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var accountName: String
    private lateinit var accountType: String

    override fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val currentUser = currentUserProvider.currentUser.blockingGet()

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
            if (System.currentTimeMillis() - appPreferences.getPhoneBookIntegrationLastRun(0L) <
                DateConstants.DAYS_DIVIDER *
                DateConstants.HOURS_DIVIDER *
                DateConstants.MINUTES_DIVIDER *
                DateConstants.SECOND_DIVIDER
            ) {
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
            val currentLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0]!!.country

            val map = mutableMapOf<String, Any>()
            map["location"] = currentLocale
            map["search"] = deviceContactsWithNumbers

            val json = Gson().toJson(map)

            ncApi.searchContactsByPhoneNumber(
                ApiUtils.getCredentials(currentUser.username, currentUser.token),
                ApiUtils.getUrlForSearchByNumber(currentUser.baseUrl!!),
                json.toRequestBody("application/json".toMediaTypeOrNull())
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ContactsByNumberOverall> {
                    override fun onComplete() {
                        // unused atm
                    }

                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(foundContacts: ContactsByNumberOverall) {
                        when (foundContacts.ocs?.meta?.statusCode) {
                            HTTP_CODE_TOO_MANY_REQUESTS -> {
                                Toast.makeText(
                                    context,
                                    context.resources.getString(
                                        R.string.nc_settings_phone_book_integration_phone_number_dialog_429
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else -> {
                                val contactsWithAssociatedPhoneNumbers = foundContacts.ocs!!.map
                                deleteLinkedAccounts(contactsWithAssociatedPhoneNumbers)
                                createLinkedAccounts(contactsWithAssociatedPhoneNumbers)
                            }
                        }
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
                    val id = contactCursor.getString(contactCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val lookup =
                        contactCursor.getString(
                            contactCursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                        )
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
                        rawContactsCursor.getString(
                            rawContactsCursor.getColumnIndexOrThrow(ContactsContract.Data.LOOKUP_KEY)
                        )
                    val contactId =
                        rawContactsCursor.getString(
                            rawContactsCursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                        )

                    if (contactsWithAssociatedPhoneNumbers == null ||
                        !contactsWithAssociatedPhoneNumbers.containsKey(lookupKey)
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

        fun createOps(
            cloudId: String,
            numbers: MutableList<String>,
            displayName: String?
        ): ArrayList<ContentProviderOperation> {
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
                            context.resources.getString(R.string.nc_phone_book_integration_chat_via),
                            accountName
                        )
                    )
                    .build()
            )
            return ops
        }

        fun createLinkedAccount(lookupKey: String, cloudId: String) {
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            val lookupContactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
            val contactCursor = context.contentResolver.query(lookupContactUri, null, null, null, null)

            if (contactCursor != null) {
                if (contactCursor.count > 0) {
                    contactCursor.moveToFirst()

                    val id = contactCursor.getString(contactCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    if (hasLinkedAccount(id)) {
                        return
                    }

                    val numbers = getPhoneNumbersFromDeviceContact(id)
                    val displayName = ContactUtils.getDisplayNameFromDeviceContact(context, id)

                    if (displayName == null) {
                        return
                    }

                    val ops = createOps(cloudId, numbers, displayName)

                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (e: OperationApplicationException) {
                        Log.e(TAG, "", e)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "", e)
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
                        phonesNumbersCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
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
        private const val HTTP_CODE_TOO_MANY_REQUESTS: Int = 429

        fun run(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                WorkManager.getInstance().enqueue(
                    OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                        .setInputData(Data.Builder().putBoolean(KEY_FORCE, false).build()).build()
                )
            }
        }

        fun checkPermission(activity: Activity, context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS),
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
