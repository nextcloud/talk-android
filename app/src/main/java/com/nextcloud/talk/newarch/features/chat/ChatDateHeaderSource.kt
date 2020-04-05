package com.nextcloud.talk.newarch.features.chat

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.HeaderSource
import com.stfalcon.chatkit.utils.DateFormatter
import java.util.*

class ChatDateHeaderSource(private val context: Context, private val elementType: Int) : HeaderSource<ChatElement, String>() {
    // Store the last header that was added, even if it belongs to a previous page.
    private var headersAlreadyAdded = mutableListOf<String>()

    override fun dependsOn(source: Source<*>) = source is ChatViewLiveDataSource

    override fun getElementType(data: Data<ChatElement, String>): Int {
        return elementType
    }

    override fun areItemsTheSame(first: Data<ChatElement, String>, second: Data<ChatElement, String>): Boolean {
        return first == second
    }
    override fun computeHeaders(page: Page, list: List<ChatElement>): List<Data<ChatElement, String>> {
        val results = arrayListOf<Data<ChatElement, String>>()
        headersAlreadyAdded = mutableListOf()
        var dateHeader = ""
        for (chatElement in list) {
            if (chatElement.data is ChatMessage) {
                dateHeader = formatDate(chatElement.data.createdAt)
                if (!headersAlreadyAdded.contains(dateHeader)) {
                    results.add(Data(chatElement, dateHeader))
                    headersAlreadyAdded.add(dateHeader)
                }
            }
        }

        return results
    }

    private fun formatDate(date: Date): String {
        return when {
            DateFormatter.isToday(date) -> {
                context.getString(R.string.nc_date_header_today)
            }
            DateFormatter.isYesterday(date) -> {
                context.resources.getString(R.string.nc_date_header_yesterday)
            }
            else -> {
                DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR)
            }
        }
    }

}