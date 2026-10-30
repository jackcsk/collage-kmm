package com.jackcheung.collage.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

/** Info.plist key holding the app group the share extension writes to. */
private const val APP_GROUP_KEY = "CollageAppGroup"

/**
 * Takes the images the share extension (iosApp/ShareExtension) left in the app group's `Inbox`.
 * The extension writes each share into a hidden folder and renames it to a timestamp when it is
 * complete; the newest complete share wins and older ones are deleted.
 */
internal fun takeSharedPhotos(): List<PhotoRef> {
    val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return emptyList()
    val files = NSFileManager.defaultManager
    val container = files.containerURLForSecurityApplicationGroupIdentifier(group)?.path ?: return emptyList()
    val inbox = "$container/Inbox"
    val shares = files.visibleNames(inbox).sorted()
    val newest = shares.lastOrNull() ?: return emptyList()
    val images = files.visibleNames("$inbox/$newest").sorted().map { "$inbox/$newest/$it" }
    val photos = IosMediaRepository.adoptFiles(images)
    shares.forEach { files.removeItemAtPath("$inbox/$it", error = null) }
    return photos
}

private fun NSFileManager.visibleNames(path: String): List<String> =
    contentsOfDirectoryAtPath(path, error = null).orEmpty().map { it as String }.filterNot { it.startsWith(".") }
