package com.jackcheung.collage.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.jackcheung.collage.CollageApp
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.platform.SharedPhotoInbox

class MainActivity : ComponentActivity() {
    private val inbox = SharedPhotoInbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity has already handed its share to the view model.
        if (savedInstanceState == null) inbox.deliver(sharedPhotos(intent))
        setContent {
            CollageApp(inbox)
        }
    }
}

/** Images sent with ACTION_SEND or ACTION_SEND_MULTIPLE; the intent grants read access to them. */
private fun sharedPhotos(intent: Intent): List<PhotoRef> {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    return uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.map { PhotoRef(it.toString()) }
}
