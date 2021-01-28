package io.muun.apollo.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

object Clipboard {
    fun write(content: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            getClipboardManager().setPrimaryClip(
                    ClipData.newPlainText("main", content)
            );
        }
    }

    fun read(): String {
        var content = ""

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            content = getClipboardManager().primaryClip!!.getItemAt(0).text.toString()
        }

        return content
    }

    private fun getClipboardManager(): ClipboardManager {
        return InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}