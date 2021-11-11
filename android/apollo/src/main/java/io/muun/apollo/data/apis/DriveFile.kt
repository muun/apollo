package io.muun.apollo.data.apis

class DriveFile(
    val id: String,
    val revisionId: String,
    val name: String,
    val mimeType: String,
    val size: Int,
    val link: String,
    val parent: DriveFile? = null,
    val properties: Map<String, String> = mapOf()
)