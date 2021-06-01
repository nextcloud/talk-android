package com.nextcloud.talk.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.nextcloud.talk.R
import fr.dudie.nominatim.model.Address

class GeocodingAdapter(context: Context, val dataSource: List<Address>) : BaseAdapter() {

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView = inflater.inflate(R.layout.geocoding_item, parent, false)

        val nameView = rowView.findViewById(R.id.name) as TextView

        val address = getItem(position) as Address
        nameView.text = address.displayName

        return rowView
    }
}


