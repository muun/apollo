package io.muun.apollo.presentation.presenters

import android.os.Bundle
import com.nhaarman.mockitokotlin2.*
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.apollo.presentation.ui.fragments.enter_email.CreateEmailParentPresenter
import io.muun.apollo.presentation.ui.fragments.enter_email.CreateEmailPresenter
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject

class CreateEmailPresenterTest: BasePresentationTest() {

    private class TestRxAndroidSchedulerHook: RxAndroidSchedulersHook() {
        override fun getMainThreadScheduler(): rx.Scheduler? {
            return Schedulers.trampoline()
        }
    }

    @Before
    fun before() {
        RxAndroidPlugins.getInstance().registerSchedulersHook(TestRxAndroidSchedulerHook())
    }

    @After
    fun after() {
        RxAndroidPlugins.getInstance().reset()
    }

    @Test
    fun `should subscribe to parent result`() {
        val (presenter, parent, submitEmailResult) = sharedInit()

        presenter.setUp(Bundle())
        verify(parent, times(1)).watchSubmitEmail()
    }

    @Test
    fun `should display loading while waiting`() {
        val (presenter, parent, submitEmailResult) = sharedInit()

        presenter.setUp(Bundle())
        submitEmailResult.onNext(ActionState.createLoading())

        verify(presenter.view).setLoading(true)
    }

    @Test
    fun `should stop on error`() {
        val (presenter, parent, submitEmailResult) = sharedInit()
        val error = Throwable()

        presenter.setUp(Bundle())
        submitEmailResult.onNext(ActionState.createError(error))

        verify(presenter.view, times(1)).setLoading(false)
    }

    @Test
    fun `should not submit invalid emails`() {
        val (presenter, parent, submitEmailResult) = sharedInit()

        presenter.submitEmail("")
        presenter.submitEmail("xx")
        presenter.submitEmail("a@b")
        presenter.submitEmail("cccc.ssss")

        verify(parent, never()).submitEmail(any())
    }

    @Test
    fun `should submit valid emails`() {
        val (presenter, parent, submitEmailResult) = sharedInit()

        presenter.submitEmail("a@b.c")

        verify(parent, times(1)).submitEmail(any())
        verifyNoMoreInteractions(parent)
    }

    private data class SharedInit(
        val presenter: CreateEmailPresenter,
        val parent: CreateEmailParentPresenter,
        val submitEmailResult: BehaviorSubject<ActionState<Void>>
    )

    /**
     * Create basic objects shared by all test cases.
     */
    private fun sharedInit(): SharedInit {
        val submitEmailResult = BehaviorSubject.create<ActionState<Void>>()

        val parent = mock<CreateEmailParentPresenter> {
            on { watchSubmitEmail() } doReturn(submitEmailResult)
        }

        val presenter = object: CreateEmailPresenter() {
            override fun setUpDeprecatedClientVersionCheck() {}
            override fun setUpSessionExpiredCheck() {}
            override fun setUpNetworkInfo() {}
            override fun handleError(error: Throwable) {}
        }

        presenter.setParentPresenter(parent)
        presenter.setView(mock())

        return SharedInit(presenter, parent, submitEmailResult)
    }
}