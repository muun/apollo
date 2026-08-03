package io.muun.apollo.domain.utils

import io.muun.apollo.domain.analytics.Analytics
import javax.inject.Inject

/**
 * List of traces added into the app. The format is PREFIX_CONST("PREFIX_ string")
 */
enum class TraceLabel(val value: String) {
    /** E2E go kit generation*/
    EK_E2E_NEW_KIT_GENERATION("EK_ E2E new kit generation"),

    /** The go rendering when we already have the data.*/
    EK_NEW_PDF_GENERATION("EK_ New Emergency Kit PDF generation"),

    /** Fetching the ekit data for the go kit*/
    EK_NEW_DATA_FETCHING("EK_ New Emergency Kit data fetching"),

    /** Fetching the ekit data for the css/html kit*/
    EK_LEGACY_DATA_FETCHING("EK_ Legacy Emergency Kit data fetching"),

    /** The css/html rendering when we already have the data.*/
    EK_LEGACY_PDF_GENERATION("EK_ LibwalletBridge.generateEmergencyKit"),

    /** E2E CSS/HTML kit generation*/
    EK_E2E_LEGACY_KIT_GENERATION("EK_ E2E legacy kit generation"),
}

// Child label constants for emergency kit data fetching traces:
const val EK_CHILD_USER_KEY = "user_key"
const val EK_CHILD_USER_FINGERPRINT = "user_fp"
const val EK_CHILD_MUUN_KEY = "muun_key"
const val EK_CHILD_MUUN_FINGERPRINT = "muun_fp"
const val EK_CHILD_RC_CHECKSUM = "rc_checksum"

// Child labels for the Go render profiling forwarded from libwallet.
const val EK_CHILD_GO_LOAD_TRANSLATIONS = "go_load_translations_ms"
const val EK_CHILD_GO_REGISTER_FONTS = "go_register_fonts_ms"
const val EK_CHILD_GO_REGISTER_IMAGES = "go_register_images_ms"
const val EK_CHILD_GO_COMPONENTS_RENDERING = "go_components_rendering_ms"
const val EK_CHILD_GO_CREATE_AND_SAVE_ON_DISK = "go_create_and_save_on_disk_ms"
const val EK_CHILD_GO_TOTAL_HEAP_ALLOCATED = "go_total_heap_allocated_bytes"
const val EK_CHILD_GO_TOTAL_OBJECTS_ALLOCATED = "go_total_objects_allocated"
const val EK_CHILD_GO_EMBED_METADATA = "go_embed_metadata_ms"
const val EK_CHILD_GO_TOTAL_INSIDE_GO = "go_total_inside_go_ms"

/** Factory for timing traces. Inject this and call [start] to begin measuring. */
class TimeTracker @Inject constructor(private val analytics: Analytics) {

    /** Start a new trace for [label]. Call [Trace.finish] (or use `use {}`) to report. */
    fun start(label: TraceLabel): Trace = Trace(label.value, analytics)
}
