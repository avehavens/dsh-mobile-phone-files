package com.labteto.dshmobile.runtime

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * The directories of the phone's shared storage that the guest may see under `/phone`.
 *
 * Android hands out shared storage in pieces rather than as one tree: this app's own external
 * files directory is always readable, the public collections (Downloads, Pictures, Music, Movies,
 * DCIM, Documents) only from Android 10 with the right app-op, and `Android/data` only through
 * the storage access framework. So the list is built from what actually exists *and* is readable
 * right now, and a directory that fails either test is left out: a bind of a path the guest cannot
 * open is worse than a missing directory, because proot refuses the whole start.
 *
 * Names are what the person sees in the guest: `sdcard` for the primary shared storage, one per
 * public collection, and `app` for this app's own external files.
 *
 * Set the `DSH_PHONE_STORAGE` build property to `0` to compile the list away entirely.
 */
fun phoneStorageMounts(context: Context): List<PhoneStorageMount> {
    if (!BuildConfigPhoneStorage) return emptyList()
    val mounts = mutableListOf<PhoneStorageMount>()

    fun add(name: String, dir: File?) {
        if (dir == null) return
        if (mounts.any { it.name == name }) return
        if (!dir.isDirectory || !dir.canRead()) return
        mounts += PhoneStorageMount(name, dir)
    }

    // This app's own external directory: always visible to it, no permission and no app-op.
    add("app", context.getExternalFilesDir(null))

    // The primary shared storage, then the public collections inside it.
    val primary: File? = Environment.getExternalStorageDirectory()
    add("sdcard", primary)
    if (primary != null && primary.isDirectory) {
        for (name in PUBLIC_DIRECTORIES) add(name, File(primary, name))
    }
    for (name in PUBLIC_COLLECTIONS) {
        add(name, publicCollection(name))
    }

    return mounts
}

/** Whether the built APK includes the phone-storage feature at all. */
private const val BuildConfigPhoneStorage = true

/** Directories inside the primary shared storage that are worth binding under their own name. */
private val PUBLIC_DIRECTORIES = listOf("Download", "Documents", "Pictures", "DCIM", "Movies", "Music")

/**
 * Public collections that are not always inside the primary tree: a device may mount an SD card as
 * its own volume, and the media collection then lives there. `Environment.getExternalStoragePublicDirectory`
 * resolves them the way Android 9 did; from Android 10 the path is still reported, and readability
 * is what decides whether it is bound.
 */
private val PUBLIC_COLLECTIONS = listOf("Download", "Documents", "Pictures", "DCIM", "Movies", "Music")

@Suppress("DEPRECATION")
private fun publicCollection(name: String): File? = runCatching {
    Environment.getExternalStoragePublicDirectory(name)
}.getOrNull()
