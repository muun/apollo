package io.muun.apollo.presentation.ui.base

import android.content.Context
import android.content.Intent
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.view.MuunHeader
import timber.log.Timber

class SingleFragmentActivityImpl :
    SingleFragmentActivity<SingleFragmentPresenter<SingleFragmentView, ParentPresenter>>() {

    companion object {
        private const val FRAGMENT_CLASS = "fragmentClass"

        fun getStartActivityIntent(
            context: Context,
            fragment: Class<out SingleFragment<*>>,
        ): Intent {

            Timber.i("SingleFragmentActivityImpl: startActivityIntent $fragment")

            val intent = Intent(context, SingleFragmentActivityImpl::class.java)
            intent.putExtra(FRAGMENT_CLASS, fragment)
            return intent
        }
    }

    @BindView(R.id.header)
    lateinit var headerView: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_single_fragment

    override fun getFragmentsContainer() =
        R.id.container

    override fun getHeader(): MuunHeader =
        headerView

    override fun getInitialFragment(): SingleFragment<out SingleFragmentPresenter<*, *>> {

        val fragmentClass: Class<*> = argumentsBundle.getSerializable(FRAGMENT_CLASS) as Class<*>

        Timber.i("SingleFragmentActivityImpl: getInitialFragment $fragmentClass")

        try {
            return fragmentClass.getConstructor()
                .newInstance() as SingleFragment<out SingleFragmentPresenter<*, *>>
        } catch (e: NoSuchMethodException) {
            // TODO: Log something nice so developers know how to fix this
            throw e
        }
    }
}