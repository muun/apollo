package io.muun.apollo.domain.action.user

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import io.muun.apollo.BaseTest
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.UserPreferencesRepository
import io.muun.apollo.domain.model.UserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.mockito.InjectMocks
import org.mockito.Mock
import rx.Completable
import rx.Observable

class UpdateUserPreferencesActionTest: BaseTest() {

    @Mock
    private lateinit var houstonClient: HoustonClient

    @Mock
    private lateinit var repository: UserPreferencesRepository

    @InjectMocks
    private lateinit var action: UpdateUserPreferencesAction

    @Before
    fun before() {
        doReturn(Observable.just(
                UserPreferences(strictMode = false, seenNewHome = false, seenLnurlFirstTime = false)
        )).whenever(repository).watch()
    }

    @Test
    fun update() {

        whenever(houstonClient.updateUserPreferences(argThat { prefs ->
            assertTrue(prefs.strictMode)
            assertFalse(prefs.seenNewHome)
            true
        })).thenReturn(Completable.complete())

        doNothing().whenever(repository).update(argThat { prefs ->
            assertTrue(prefs.strictMode)
            assertFalse(prefs.seenNewHome)
            true
        })

        action.actionNow { prefs ->
            assertFalse(prefs.strictMode)
            assertFalse(prefs.seenNewHome)

            prefs.copy(strictMode = true)
        }
    }
}