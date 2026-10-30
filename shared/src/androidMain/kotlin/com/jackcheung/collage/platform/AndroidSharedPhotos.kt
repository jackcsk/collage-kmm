package com.jackcheung.collage.platform

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat

/**
 * Takes the images of an ACTION_SEND / ACTION_SEND_MULTIPLE launch (the intent filter is in the app
 * manifest). Only once per activity: a recreated one has already handed them over.
 */
@Composable
actual fun rememberSharedPhotoInbox(): SharedPhotoInbox {
    val intent = LocalActivity.current?.intent
    val inbox = remember { SharedPhotoInbox() }
    var taken by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!taken && intent != null) inbox.deliver(sharedPhotos(intent))
        taken = true
    }
    return inbox
}

/** The intent grants read access to these, so no photo permission is needed. */
private fun sharedPhotos(intent: Intent): List<PhotoRef> {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    return uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.map { PhotoRef(it.toString()) }
}
