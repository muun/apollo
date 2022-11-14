package io.muun.apollo.presentation.ui.base

import android.content.Context
import android.content.Intent
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.view.MuunHeader

class SingleFragmentActivityImpl :
    SingleFragmentActivity<SingleFragmentPresenter<SingleFragmentView, ParentPresenter>>() {

    companion object {
        private const val FRAGMENT_CLASS = "fragmentClass"

        fun getStartActivityIntent(
            context: Context,
            fragment: Class<out SingleFragment<*>>,
        ): Intent {

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

    override fun initializeUi() {
        super.initializeUi()
        headerView.attachToActivity(this)
    }

    override fun getInitialFragment(): BaseFragment<out Presenter<*>> {
        val fragmentClass: Class<*> = argumentsBundle.getSerializable(FRAGMENT_CLASS) as Class<*>
        try {
            return fragmentClass.getConstructor().newInstance() as BaseFragment<out Presenter<*>>
        } catch (e: NoSuchMethodException) {
            // TODO: Log something nice so developers know how to fix this
            throw e
        }
    }
}