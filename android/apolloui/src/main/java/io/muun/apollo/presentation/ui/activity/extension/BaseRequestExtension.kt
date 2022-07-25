package io.muun.apollo.presentation.ui.activity.extension

import android.os.Bundle
import android.view.View
import icepick.Bundler
import icepick.State
import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.common.utils.Preconditions

abstract class BaseRequestExtension : ActivityExtension() {

    @JvmField
    @State
    var globalRequestCodeCounter = 10000 // avoid clashing with typical request codes

    interface BaseCaller {
        val mId: Int
    }

    fun registerRequestFromCaller(caller: BaseCaller, viewRequestCode: Int): Int {
        Preconditions.checkArgument(caller.mId != View.NO_ID)
        val request = CallerRequest()
        request.viewId = caller.mId
        request.viewRequestCode = viewRequestCode
        val globalRequestCode = uniqueRequestCode
        registerRequestFromCaller(request, globalRequestCode)

        Crashlytics.logBreadcrumb(
            """startActivityForResult:
                globalRequestCode: $globalRequestCode
                request.viewRequestCode: ${request.viewRequestCode}
                request.viewId: ${request.viewId}
                view: ${caller.javaClass}
                 """
        )

        return globalRequestCode
    }

    protected abstract fun registerRequestFromCaller(request: CallerRequest, globalRequestCode: Int)

    @Suppress("UNCHECKED_CAST")
    fun <T : BaseCaller?> findCaller(request: CallerRequest?, callerType: Class<T>): T? {
        if (request == null) {
            return null
        }
        val view = activity.findViewById<View>(request.viewId)
        if (callerType.isInstance(view)) {
            return view as T
        }
        val fragment = activity.supportFragmentManager
            .findFragmentById(request.viewId)
        if (callerType.isInstance(fragment)) {
            return fragment as T?
        }
        return if (callerType.isInstance(activity)) {
            activity as T
        } else null
    }

    private val uniqueRequestCode: Int
        get() = ++globalRequestCodeCounter

    class CallerRequest {
        // Making fields public for Jackson to de/serialize
        var viewId = 0

        @JvmField
        var viewRequestCode = 0
    }

    class RequestMapBundler : Bundler<Map<Int, CallerRequest>> {

        override fun put(key: String, map: Map<Int, CallerRequest>, bundle: Bundle) {
            bundle.putString(
                key,
                SerializationUtils.serializeMap(Int::class.java, CallerRequest::class.java, map)
            )
        }

        override fun get(key: String, bundle: Bundle): Map<Int, CallerRequest> {
            return SerializationUtils.deserializeMap(
                Int::class.java,
                CallerRequest::class.java,
                bundle.getString(key)
            )
        }
    }
}