package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.HeaderSource

class ContactsHeaderSource(private val context: Context, private val elementType: Int): HeaderSource<Participant, String>() {

    // Store the last header that was added, even if it belongs to a previous page.
    private var lastHeader: String = ""

    override fun dependsOn(source: Source<*>) = source is ContactsViewSource

    override fun computeHeaders(page: Page, list: List<Participant>): List<Data<Participant, String>> {
        val results = arrayListOf<Data<Participant, String>>()
        for (participant in list) {
            val header = when (participant.source) {
                "users" -> {
                    context.getString(R.string.nc_contacts)
                }
                "groups" -> {
                    context.getString(R.string.nc_groups)
                }
                "emails" -> {
                    context.getString(R.string.nc_emails)
                }
                "circles" -> {
                    context.getString(R.string.nc_circles)
                }
                else -> {
                    context.getString(R.string.nc_others)
                }
            }

            if (header != lastHeader) {
                results.add(Data(participant, header))
                lastHeader = header
            }
        }

        return results
    }

    override fun getElementType(data: Data<Participant, String>): Int {
        return elementType
    }

    override fun areItemsTheSame(first: Data<Participant, String>, second: Data<Participant, String>): Boolean {
        return first == second
    }
}