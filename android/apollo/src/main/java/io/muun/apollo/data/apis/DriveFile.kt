package io.muun.apollo.data.apis

class DriveFile(
    val id: String,
    val name: String,
    val size: Int,
    val link: String,
    val parent: DriveFile? = null
)