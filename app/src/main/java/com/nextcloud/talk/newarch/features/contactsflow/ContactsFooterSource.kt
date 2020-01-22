package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.FooterSource

class ContactsFooterSource(private val context: Context, private val elementType: Int) : FooterSource<Participant, String>() {
    private var lastAnchor: Participant? = null

    override fun dependsOn(source: Source<*>): Boolean {
        return source is ContactsViewSource
    }

    override fun areItemsTheSame(first: Data<Participant, String>, second: Data<Participant, String>): Boolean {
        return first == second
    }

    override fun getElementType(data: Data<Participant, String>) = elementType

    override fun computeFooters(page: Page, list: List<Participant>): List<Data<Participant, String>> {
        val results = arrayListOf<Data<Participant, String>>()
        lastAnchor = if (list.isNotEmpty()) {
            val participant = list.takeLast(1)[0]

            if (lastAnchor == null || lastAnchor != participant) {
                results.add(Data(participant, context.getString(R.string.nc_search_for_more)))
            }

            participant
        } else {
            null
        }

        return results
    }
}