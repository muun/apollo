package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers

class RecoveryCodeSetupScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    fun tryClose() {
        toolbar.pressClose()
        checkDialogDisplayed()
        dismissAbortDialog()
    }

    fun abortFlow() {
        toolbar.pressClose()
        checkDialogDisplayed()
        confirmAbort()
    }

    fun fillForm(): List<String> {

        begin()

        val recoveryCode = readRecoveryCode()
        goNext()

        enterRecoveryCode(recoveryCode)

        confirmRecoveryCode()

        acceptConditions()

        finish()
        return recoveryCode
    }

    fun enterRecoveryCode(parts: List<String>) {
        getEditableSegments(bindRecoveryCodeSegments()).forEach {
            it.value.text = parts[it.key]
        }
    }

    fun begin() {
        pressMuunButton(R.id.priming_recovery_code_start)
    }

    fun goNext() {
        pressMuunButton(R.id.recovery_code_continue)
    }

    fun readRecoveryCode() =
        bindRecoveryCodeSegments().map { uiObject -> uiObject.text.toString() }

    fun confirmRecoveryCode() {
        pressMuunButton(R.id.accept)
    }

    private fun acceptConditions() {
        id(R.id.recovery_code_condition_1).click()
        id(R.id.recovery_code_condition_2).click()

        pressMuunButton(R.id.recovery_code_accept)
    }

    private fun finish() {
        pressMuunButton(R.id.single_action_action)
    }

    private fun checkDialogDisplayed() {
        dialog.checkDisplayed(R.string.recovery_code_abort_body)
    }

    private fun dismissAbortDialog() {
        dialog.pressCancel()
    }

    private fun confirmAbort() {
        dialog.pressAbort()
    }

    private fun bindRecoveryCodeSegments(): List<UiObject> {
        return listOf(
            input(R.id.recovery_code_box_input_1),
            input(R.id.recovery_code_box_input_2),
            input(R.id.recovery_code_box_input_3),
            input(R.id.recovery_code_box_input_4),
            input(R.id.recovery_code_box_input_5),
            input(R.id.recovery_code_box_input_6),
            input(R.id.recovery_code_box_input_7),
            input(R.id.recovery_code_box_input_8)
        )
    }

    private fun getEditableSegments(segments: List<UiObject>): Map<Int, UiObject> {
        val editableSegments = mutableMapOf<Int, UiObject>()
        for ((index, segment) in segments.withIndex()) {

            if (isEditable(segment)) {
                editableSegments[index] = segment
            }
        }

        return editableSegments
    }

    private fun isEditable(uiObject: UiObject): Boolean {
        return uiObject.isEnabled
    }
}
