package com.jackcheung.collage.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Photos another app shared into Collage, held until the app takes them. The Android activity and
 * the iOS view controller deliver into it on the main thread; a newer share replaces an untaken one.
 */
class SharedPhotoInbox {
    var pending by mutableStateOf<List<PhotoRef>>(emptyList())
        private set

    fun deliver(photos: List<PhotoRef>) {
        if (photos.isNotEmpty()) pending = photos
    }

    fun take(): List<PhotoRef> = pending.also { pending = emptyList() }
}
