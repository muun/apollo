package io.muun.apollo.domain.utils

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.muun.apollo.data.os.OS
import io.muun.apollo.data.os.secure_storage.KeyStoreProvider
import io.muun.apollo.data.os.secure_storage.SecureStoragePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.KeyStoreCorruptedError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SecureStorageNoSuchElementError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SharedPreferencesCorruptedError
import io.muun.common.utils.Encodings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

@RunWith(AndroidJUnit4::class)
class SecureStorageProviderTest {

    lateinit var storage: SecureStorageProvider
    lateinit var context: Context

    private val errors: MutableList<Throwable> = mutableListOf()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = SecureStorageProvider(
                KeyStoreProvider(context),
                SecureStoragePreferences(context)
        )
    }

    @After
    fun tearDown() {
        println(storage.debugSnapshot().toString())
        storage.wipe()
        errors.clear()
    }

    /**
     * Android Keystore is not thread-safe (see discussion on linked resources of
     * https://stackoverflow.com/a/63761020/901465). So we test that, with a some concurrent
     * access, nothing strange happens.
     */
    @Test
    fun testConcurrentAccess() {

        println("testConcurrentAccess: STARTING")
        val threads = arrayListOf<Thread>()

        for (i in 1..200) {
            val thread = Thread {

                try {
                    storage.put("hola", Encodings.stringToBytes("chau"))
                    assertEquals("chau", Encodings.bytesToString(storage.get("hola")))

                } catch (e: Throwable) {
                    errors.add(e)
                    println("testConcurrentAccess: ADD ERROR TO LIST")
                }
            }
            threads.add(thread)
        }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        println("testConcurrentAccess: errors encountered: ${errors.size}")

        if (errors.isNotEmpty()) {

            val kpi: Int
            if (OS.supportsKeyPermanentlyInvalidatedException()) {
                kpi = howManyOfType<KeyPermanentlyInvalidatedException>(errors)
                println("testConcurrentAccess: keyPermanentlyInvalidated: $kpi")
            } else {
                kpi = 0
            }

            val badPaddingException = howManyOfType<BadPaddingException>(errors)
            println("testConcurrentAccess: badPaddingException: $badPaddingException")

            val unrecoverableKeyException = howManyOfType<UnrecoverableKeyException>(errors)
            println("testConcurrentAccess: unrecoverableKeyException: $unrecoverableKeyException")

            val keyStoreCorruptedError = howManyOfType<KeyStoreCorruptedError>(errors)
            println("testConcurrentAccess: unrecoverableKeyException: $keyStoreCorruptedError")

            val sharePrefsCorrupted = howManyOfType<SharedPreferencesCorruptedError>(errors)
            println("testConcurrentAccess: sharedPreferencesCorruptedError: $sharePrefsCorrupted")

            val noSuchElementError = howManyOfType<SecureStorageNoSuchElementError>(errors)
            println("testConcurrentAccess: noSuchElementError: $noSuchElementError")

            val illegalBlockSizeException = howManyOfType<IllegalBlockSizeException>(errors)
            println("testConcurrentAccess: illegalBlockSizeException: $illegalBlockSizeException")

            val invalidKeyException = howManyOfType<InvalidKeyException>(errors)
            println("testConcurrentAccess: invalidKeyException: $invalidKeyException")

            val others = errors.size - kpi -
                    badPaddingException -
                    unrecoverableKeyException -
                    keyStoreCorruptedError -
                    sharePrefsCorrupted -
                    noSuchElementError -
                    illegalBlockSizeException -
                    invalidKeyException

            println("testConcurrentAccess: others: $others" )

            val first = errors.first()

            println(first.stackTraceToString())
        }

        assert(errors.isEmpty())
    }

    private inline fun <reified T> howManyOfType(errors: List<Throwable>) =
            errors.filter { it.isCausedByError<T>() }.size
}