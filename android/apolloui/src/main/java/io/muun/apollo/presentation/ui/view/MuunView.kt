package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import butterknife.ButterKnife
import icepick.Icepick
import io.muun.apollo.domain.errors.BugDetected
import io.muun.apollo.presentation.app.di.ApplicationComponent
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.di.FragmentComponent
import io.muun.apollo.presentation.ui.base.di.ViewComponent
import timber.log.Timber
import java.util.*
import javax.validation.constraints.NotNull

abstract class MuunView : FrameLayout,
    ExternalResultExtension.Caller,
    PermissionManagerExtension.PermissionRequester {

    companion object {
        private const val OWN_STATE = "own-state"
        private const val CHILD_STATE = "child-state"
    }

    constructor(context: Context) : super(context) {
        setUp(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setUp(context, attrs)
    }

    constructor(c: Context, a: AttributeSet?, d: Int) : super(c, a, d) {
        setUp(c, a)
    }

    @get:LayoutRes
    protected abstract val layoutResource: Int

    protected val root: ViewGroup
        get() = getChildAt(0) as ViewGroup

    // Ugly but taken from several other projects that resort to this:
    // - Android Support Library:
    //  https://android.googlesource.com/platform/frameworks/support/+/refs/heads/marshmallow-release/v7/mediarouter/src/android/support/v7/app/MediaRouteButton.java
    // - Glide (one of our deps)
    //  https://www.codota.com/web/assistant/code/rs/5c7cb62c2ef5570001df7f51#L309
    // - FB's Litho
    //  https://www.codota.com/web/assistant/code/rs/5c7cb38e2ef5570001df68a9#L65
    // - FB's Android SDK
    //  https://www.codota.com/web/assistant/code/rs/5c7c927f2f7dae000163adf4#L179
    // - FB's Stetho
    //  https://www.codota.com/web/assistant/code/rs/5c7cabe42ef5570001deea49#L46
    protected val activity: BaseActivity<*>
        get() {
            var context = context
            while (context is ContextWrapper) {
                if (context is BaseActivity<*>) {
                    return context
                }
                context = context.baseContext
            }

            val error = BugDetected(
                "MuunView's context is not a BaseActivity in ${this.javaClass.simpleName}"
            )
            Timber.e(error)
            throw error // throw to avoid return null, all users assume this isn't null
        }

    // This is here for easy access of child views that need it. Let's try to avoid injection in
    // this base class so we don't perform unnecessary DI on all our views.
    protected val component: ViewComponent by lazy { activity.applicationComponent.viewComponent() }

    @CallSuper
    protected open fun setUp(context: Context, attrs: AttributeSet?) {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // When attachToRoot is false, inflate returns innerView, otherwise it returns
        // parentView (in this case, `this`). See its javadoc
        val innerView = inflater.inflate(layoutResource, this, false)
        addView(innerView)
        ButterKnife.bind(this, innerView)

        // WAIT: when did we change the code to add this `innerView` nonsense, instead of using
        // ourselves as the root layout like in the good old times?

        // Well, glad you asked. As you know, one of the guarantees of MuunView is that internal IDs
        // will never cause problems if the same IDs are assigned to our ancestors or siblings in
        // the view tree (which may be other instances of this class, with every ID shared).

        // This is critical to the processes of finding children and saving/restoring state. View
        // parents can do whatever they want, we handle our internal tree. We're smart that way.

        // This ran into a silly problem: what if our parent assigns an ID to THIS VERY VIEW, and
        // that ID is also present in our internal tree? See, when finding a view by ID, the first
        // attempted match is the view doing the finding (that's us), so we're going to match
        // before any of our children. Yeah, this actually happened during development.

        // The solution was changing the binding to use the inflated child as the binding source.

        // In doing so, we split the layout inflation in two steps: inflate and addView(innerView).
        // This has the disadvantage of banning the use of <merge> (when our first child is actually
        // us).

        // So, a lot of lessons learned and a huge comment left mostly because I wanted to get it
        // out of my chest. Hope you enjoyed this broadcast.
    }

    override fun onSaveInstanceState(): Parcelable {
        // Our children may have XML IDs that MuunView subclasses might want to pick up. Two
        // instances of the same subclass will thus share their internal IDs. For example:

        // <MuunViewSubclass>
        //   <EditText android:id="foo" />
        // </>

        // If there are two instances of this View in the same layout, they will have problems
        // persisting and restoring state. They will compete to save their state, overwriting each
        // other, and then they will both restore the *same* state (originally belonging to one
        // of the two). This is not theoretical, it happens on recreation and config changes,
        // systematically.

        // What we need to do is save the state of MuunView children manually, without resorting
        // to the "global" (per-layout) ID-to-state map. As long as the MuunView itself has a
        // unique ID, nothing will clash.

        val state = Bundle()
        val ownState = Icepick.saveInstanceState(this, super.onSaveInstanceState())
        val childState = SparseArray<Parcelable>()
        for (i in 0 until childCount) {
            getChildAt(i).saveHierarchyState(childState)
        }

        state.putParcelable(OWN_STATE, ownState)
        state.putSparseParcelableArray(CHILD_STATE, childState)
        return state
    }

    override fun onRestoreInstanceState(parcelable: Parcelable) {
        if (parcelable is Bundle) {
            val ownState = parcelable.getParcelable<Parcelable>(OWN_STATE)
            val childState = parcelable.getSparseParcelableArray<Parcelable>(CHILD_STATE)
            for (i in 0 until childCount) {
                getChildAt(i).restoreHierarchyState(childState)
            }
            super.onRestoreInstanceState(Icepick.restoreInstanceState(this, ownState))
        } else {
            super.onRestoreInstanceState(parcelable)
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        // Do not propagate default behavior to children, since we'll handle this ourselves.
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        // Do not propagate default behavior to children, since we'll handle this ourselves.
        dispatchThawSelfOnly(container)
    }

    protected fun <T : View> findViewsByTag(tag: String): List<T> {
        val results: MutableList<T> = LinkedList()
        addViewsByTagRecursively(tag, this, results)
        return ArrayList(results) // caller can index in O(1)
    }

    private fun <T : View> addViewsByTagRecursively(
        tag: String,
        root: ViewGroup,
        results: MutableList<T>
    ) {
        val childCount = root.childCount
        for (i in 0 until childCount) {
            val child = root.getChildAt(i)
            if (tag == child.tag) {
                results.add(child as T)
            }
            if (child is ViewGroup) {
                addViewsByTagRecursively(tag, child, results)
            }
        }
    }

    protected fun showDrawerDialog(dialog: DialogFragment) {
        activity.showDrawerDialog(dialog)
    }

    protected fun requestExternalResult(requestCode: Int, intent: Intent?) {
        activity.requestExternalResult(this, requestCode, intent)
    }

    protected fun requestExternalResult(requestCode: Int, dialog: DialogFragment) {
        activity.requestExternalResult(this, requestCode, dialog)
    }

    override fun onExternalResult(requestCode: Int, resultCode: Int, data: Intent?) {}

    protected fun requestPermissions(vararg permissions: String) {
        activity.requestPermissions(this, *permissions)
    }

    protected fun allPermissionsGranted(vararg permissions: String): Boolean {
        return activity.allPermissionsGranted(*permissions)
    }

    /**
     * Gets whether you should show UI with rationale for requesting a permission.
     * Return false if the permission was denied with the 'Never ask again' checkbox checked.
     * See: [PermissionManagerExtension.canShowRequestPermissionRationale]
     */
    protected fun canShowRequestPermissionRationale(permission: String): Boolean {
        return activity.canShowRequestPermissionRationale(permission)
    }

    override fun onPermissionsGranted(grantedPermissions: Array<String>) {}
    override fun onPermissionsDenied(deniedPermissions: Array<String>) {}
}