package com.jackcheung.collage.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Photos another app shared into Collage, held until the app takes them. Filled on the main thread
 * by [rememberSharedPhotoInbox]; a newer share replaces an untaken one.
 */
class SharedPhotoInbox {
    var pending by mutableStateOf<List<PhotoRef>>(emptyList())
        private set

    fun deliver(photos: List<PhotoRef>) {
        if (photos.isNotEmpty()) pending = photos
    }

    fun take(): List<PhotoRef> = pending.also { pending = emptyList() }
}

/** The inbox for this app instance, filled from the platform's share entry (Android intent, iOS extension). */
@Composable
expect fun rememberSharedPhotoInbox(): SharedPhotoInbox
