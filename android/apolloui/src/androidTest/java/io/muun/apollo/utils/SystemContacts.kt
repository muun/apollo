package io.muun.apollo.utils

import io.muun.apollo.data.external.Gen

/**
 * Utility object to interact with Android Contacts/Address Book.
 */
object SystemContacts {

    /**
     * Add new contact to the Android's Address Book (No UI involved, just altering contact tables)
     */
    fun create(name: String, phone: String) {
        val accountName = Gen.alpha(15) // random account for this contact so it's the only result

        // Create an empty contact:
        val contactId = createRawContact(accountName)

        setNameData(contactId, name)
        setPhoneNumberData(contactId, phone)
    }

    /**
     * Clear all contacts from Android Address Book.
     */
    fun clear() {
        SystemCommand.clearData("com.android.providers.contacts")
    }

    /**
     * Effectively creates a new contact. No Display name nor phone number data associated to it.
     */
    private fun createRawContact(accountName: String): String {
        val accountType = "muunTest" // whatever

        adb("content", "insert",
            "--uri", "content://com.android.contacts/raw_contacts",
            "--bind", "account_type:s:$accountType",
            "--bind", "account_name:s:$accountName"
        )

        // Wait for Contacts Content Provider make written data available for reading
        Thread.sleep(25)

        // Get new raw contact's id to return it
        val output = adb("content", "query",
            "--uri", "content://com.android.contacts/raw_contacts",
            "--projection", "_id",
            "--where", "account_name=\"$accountName\""
        )

        val regex = "_id=(\\d+)".toRegex()
        val matchResult = regex.find(output)
        val (id) = matchResult!!.destructured

        return id
    }

    /**
     * Set name data to a raw contact.
     */
    private fun setNameData(id: String, name: String) {
        adb("content", "insert",
            "--uri", "content://com.android.contacts/data",
            "--bind", "raw_contact_id:i:$id",
            "--bind", "mimetype:s:vnd.android.cursor.item/name",
            "--bind", "data1:s:$name"
        )
    }

    /**
     * Set phone number data to a raw contact.
     */
    private fun setPhoneNumberData(id: String, phone: String) {
        val phoneType = "muunTest" // whatever
        val phoneName = Gen.alpha(10)

        adb("content", "insert",
            "--uri", "content://com.android.contacts/data",
            "--bind", "raw_contact_id:i:$id",
            "--bind", "mimetype:s:vnd.android.cursor.item/phone_v2",
            "--bind", "data1:s:$phone",
            "--bind", "data2:s:$phoneType",
            "--bind", "data3:s:Label_$phoneName"
        )
    }
}