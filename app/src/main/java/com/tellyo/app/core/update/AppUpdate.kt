package com.tellyo.app.core.update

import java.io.File

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

interface UpdateRepository {
    suspend fun checkForUpdate(): AppUpdate?
}

interface UpdateDownloader {
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File
}
