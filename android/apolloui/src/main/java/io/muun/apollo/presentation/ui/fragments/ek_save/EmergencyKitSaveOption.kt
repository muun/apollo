package io.muun.apollo.presentation.ui.fragments.ek_save

enum class EmergencyKitSaveOption(val requestCode: Int) {
    SHARE_MANUALLY(1),
    SEND_EMAIL(2),
    SEND_EMAIL_PICKER(3),
    SAVE_TO_DRIVE(4)
}