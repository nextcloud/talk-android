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

package com.nextcloud.talk.jobs

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
import android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.work.*
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Controller
import com.google.gson.Gson
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.search.ContactsByNumberOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.RequestBody
import javax.inject.Inject
import com.nextcloud.talk.R


@AutoInjector(NextcloudTalkApplication::class)
class ContactAddressBookWorker(val context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val currentUser = userUtils.currentUser

        if (currentUser == null) {
            Log.e(javaClass.simpleName, "No current user!")
            return Result.failure()
        }
        // Check if run already at the date
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!force) {
            if (System.currentTimeMillis() - appPreferences.getPhoneBookIntegrationLastRun(0L) < 24 * 60 * 60 * 1000) {
                Log.d(TAG, "Already run within last 24h")
                return Result.success()
            }
        }

        AccountManager.get(context).addAccountExplicitly(Account(ACCOUNT_NAME, ACCOUNT_TYPE), "", null)

        // collect all contacts with phone number
        val contactsWithNumbers = collectPhoneNumbers()

        val currentLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0].country

        val map = mutableMapOf<String, Any>()
        map["location"] = currentLocale
        map["search"] = contactsWithNumbers

        val json = Gson().toJson(map)

        ncApi.searchContactsByPhoneNumber(
                ApiUtils.getCredentials(currentUser.username, currentUser.token),
                ApiUtils.getUrlForSearchByNumber(currentUser.baseUrl),
                RequestBody.create(MediaType.parse("application/json"), json))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ContactsByNumberOverall> {
                    override fun onComplete() {
                    }

                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(foundContacts: ContactsByNumberOverall) {
                        Log.d(javaClass.simpleName, "next")

                        // todo update
                        up(foundContacts)
                    }

                    override fun onError(e: Throwable) {
                        // TODO error handling
                        Log.d(javaClass.simpleName, "error")
                    }

                })

        // store timestamp 
        appPreferences.setPhoneBookIntegrationLastRun(System.currentTimeMillis())

        return Result.success()
    }

    private fun collectPhoneNumbers(): MutableMap<String, List<String>> {
        val result: MutableMap<String, List<String>> = mutableMapOf()

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
                    val numbers: MutableList<String> = mutableListOf()

                    val id = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts._ID))
                    val lookup = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY))

                    val phonesCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                            null,
                            null)

                    if (phonesCursor != null) {
                        while (phonesCursor.moveToNext()) {
                            numbers.add(phonesCursor.getString(phonesCursor.getColumnIndex(NUMBER)))
                        }

                        result[lookup] = numbers

                        phonesCursor.close()
                    }

                    contactCursor.moveToNext()
                }
            }

            contactCursor.close()
        }

        return result
    }

    private fun up(foundContacts: ContactsByNumberOverall) {
        val map = foundContacts.ocs.map
        
        // Delete all old associations (those that are associated on phone, but not in server response) 
        val rawContactUri = ContactsContract.Data.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, "Nextcloud Talk")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.nextcloud.talk2")
                .appendQueryParameter(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat")
                .build()

        // get all raw contacts
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
                    val id = rawContactsCursor.getString(rawContactsCursor.getColumnIndex(ContactsContract.RawContacts._ID))
                    val sync1 = rawContactsCursor.getString(rawContactsCursor.getColumnIndex(ContactsContract.Data.SYNC1))
                    val lookupKey = rawContactsCursor.getString(rawContactsCursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY))
                    Log.d("Contact", "Found associated: $id")

                    if (map == null || !map.containsKey(lookupKey)) {
                        if (sync1 != null) {
                            deleteAssociation(sync1)
                        }
                    }
                }
            }

            rawContactsCursor.close()
        }

        // update / change found
        if (map != null && map.isNotEmpty()) {
            for (contact in foundContacts.ocs.map) {
                val lookupKey = contact.key
                val cloudId = contact.value
                
                update(lookupKey, cloudId)
            }
        }
    }

    private fun update(uniqueId: String, cloudId: String) {
        val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, uniqueId)
        val lookupContactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
        val contactCursor = context.contentResolver.query(
                lookupContactUri,
                null,
                null,
                null,
                null)

        if (contactCursor != null) {
            if (contactCursor.count > 0) {
                contactCursor.moveToFirst()

                val id = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Contacts._ID))

                val phonesCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.Data.CONTACT_ID + " = " + id,
                        null,
                        null)

                val numbers = mutableListOf<String>()
                if (phonesCursor != null) {
                    while (phonesCursor.moveToNext()) {
                        numbers.add(phonesCursor.getString(phonesCursor.getColumnIndex(NUMBER)))
                    }

                    phonesCursor.close()
                }

                var displayName: String? = null

                val whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?"
                val whereNameParams = arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, id)
                val nameCursor = context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        null,
                        whereName,
                        whereNameParams,
                        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                if (nameCursor != null) {
                    while (nameCursor.moveToNext()) {
                        displayName = nameCursor.getString(nameCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME))
                    }
                    nameCursor.close()
                }

                if (displayName == null) {
                    return
                }

                // update entries
                val ops = ArrayList<ContentProviderOperation>()
                val rawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                        .build()
                val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
                        .build()

                ops.add(ContentProviderOperation
                        .newInsert(rawContactsUri)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                        .withValue(ContactsContract.RawContacts.AGGREGATION_MODE,
                                ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                        .withValue(ContactsContract.RawContacts.SYNC2, cloudId)
                        .build())
                ops.add(ContentProviderOperation
                        .newInsert(dataUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(NUMBER, numbers[0])
                        .build())
                ops.add(ContentProviderOperation
                        .newInsert(dataUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                        .build())
                ops.add(ContentProviderOperation
                        .newInsert(dataUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat")
                        .withValue(ContactsContract.Data.DATA1, cloudId)
                        .withValue(ContactsContract.Data.DATA2, "Chat via " + context.resources.getString(R.string.nc_app_name))
                        .build())

                try {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                } catch (e: OperationApplicationException) {
                    e.printStackTrace()
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }

            contactCursor.close()
        }
    }

    private fun deleteAssociation(id: String) {
        Log.d("Contact", "Delete associated: $id")

        val rawContactUri = ContactsContract.RawContacts.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, "Nextcloud Talk")
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.nextcloud.talk2")
                .build()


        val count = context.contentResolver.delete(rawContactUri, ContactsContract.RawContacts.SYNC2 + " LIKE \"" + id + "\"", null)
        Log.d("Contact", "deleted $count for id $id")
    }

    companion object {
        const val TAG = "ContactAddressBook"
        const val REQUEST_PERMISSION = 231
        const val KEY_FORCE = "KEY_FORCE"
        const val ACCOUNT_TYPE = "com.nextcloud.talk2"
        const val ACCOUNT_NAME = "Nextcloud Talk"

        fun run(context: Context) {
            if (ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context,
                            Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                WorkManager
                        .getInstance()
                        .enqueue(OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                                .setInputData(Data.Builder().putBoolean(KEY_FORCE, false).build())
                                .build())
            }
        }

        fun checkPermission(controller: Controller, context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context,
                            Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                controller.requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_CONTACTS), REQUEST_PERMISSION)
                return false
            } else {
                WorkManager
                        .getInstance()
                        .enqueue(OneTimeWorkRequest.Builder(ContactAddressBookWorker::class.java)
                                .setInputData(Data.Builder().putBoolean(KEY_FORCE, true).build())
                                .build())
                return true
            }
        }
    }
}
