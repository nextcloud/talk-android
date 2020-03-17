package com.nextcloud.talk.newarch.features.chat.interfaces

import android.widget.ImageView
import coil.ImageLoader

interface ImageLoaderInterface {
    fun getImageLoader(): ImageLoader
    fun loadImage(imageView: ImageView, url: String, payload: MutableMap<String, String>? = null)
}