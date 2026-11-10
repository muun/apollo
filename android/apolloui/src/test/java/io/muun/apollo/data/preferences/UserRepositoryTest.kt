package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserRepositoryTest : BaseTest() {

    private val sharedPreferences = FakeSharedPreferences()

    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns sharedPreferences
    }

    private lateinit var repository: UserRepository

    @Before
    fun setUp() {
        repository = UserRepository(context, RepositoryRegistry())
    }

    @Test
    fun `fetchHid should return null when no user is stored`() {
        // WHEN
        val hid = repository.fetchHid()

        // THEN
        assertNull(hid)
    }

    @Test
    fun `fetchHid should return the stored user's hid`() {
        // GIVEN
        val user = Gen.user()
        repository.store(user)

        // WHEN
        val hid = repository.fetchHid()

        // THEN
        assertEquals(user.hid, hid)
    }

    @Test
    fun `fetchHid should return null once the repository is cleared`() {
        // GIVEN
        repository.store(Gen.user())
        repository.clear()

        // WHEN
        val hid = repository.fetchHid()

        // THEN
        assertNull(hid)
    }
}
