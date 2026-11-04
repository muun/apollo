package io.muun.apollo.data.libwallet.grpc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.muun.apollo.data.external.DataComponentProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rpc.WalletServiceGrpc
import rpc.WalletServiceGrpc.WalletServiceBlockingStub
import rpc.WalletServiceOuterClass.DeleteRequest
import rpc.WalletServiceOuterClass.GetRequest
import rpc.WalletServiceOuterClass.SaveRequest
import rpc.WalletServiceOuterClass.Value
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GrpcChannelFactoryTest {

    companion object {
        private const val CALL_TIMEOUT_SECONDS = 30L

        // Defined in libwallet's schema.go. The key-value store rejects keys it has no
        // classification for, so the test borrows this string key and restores it afterwards.
        private const val STRING_KEY = "nightMode"

        private const val HTTP2_FRAME_BYTES = 16 * 1_024
        private const val HTTP2_INITIAL_WINDOW_BYTES = 64 * 1_024 - 1

        private const val CONCURRENT_CALLERS = 8
        private const val CALLS_PER_CALLER = 25

        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    }

    private lateinit var socketPath: String
    private lateinit var channel: ManagedChannel
    private lateinit var blockingStub: WalletServiceBlockingStub
    private lateinit var originalValue: Value

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        socketPath = (context.applicationContext as DataComponentProvider)
            .dataComponent
            .libwalletConfig()
            .socketPath

        channel = GrpcChannelFactory.create(socketPath)
        blockingStub = WalletServiceGrpc.newBlockingStub(channel)
        originalValue = get(STRING_KEY)
    }

    @After
    fun tearDown() {
        if (!::channel.isInitialized) {
            return
        }

        try {
            if (::originalValue.isInitialized) {
                if (originalValue.hasNullValue()) {
                    stub().delete(DeleteRequest.newBuilder().setKey(STRING_KEY).build())
                } else {
                    save(STRING_KEY, originalValue)
                }
            }
        } finally {
            channel.shutdownNow()
            channel.awaitTermination(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Test
    fun shouldRoundTripValuesLargerThanAFrameAndTheFlowControlWindow() {
        // GIVEN payloads on both sides of the two sizes at which the transport splits a message:
        // the HTTP/2 DATA frame and the initial flow-control window
        val sizes = listOf(
            1,
            1_024,
            HTTP2_FRAME_BYTES + 1,
            HTTP2_INITIAL_WINDOW_BYTES + 1,
            1_024 * 1_024
        )

        for (size in sizes) {
            val expected = payload(size)

            // WHEN the value is saved and read back through the channel
            save(STRING_KEY, Value.newBuilder().setStringValue(expected).build())
            val actual = get(STRING_KEY)

            // THEN it comes back byte for byte
            assertThat(actual.hasStringValue())
                .describedAs("size=$size kind")
                .isTrue()
            assertThat(actual.stringValue)
                .describedAs("size=$size content")
                .isEqualTo(expected)
        }
    }

    @Test
    fun shouldServeConcurrentCallsOverTheSharedConnection() {
        // GIVEN a stored value that spans several frames, and callers on parallel threads
        val expected = payload(2 * HTTP2_FRAME_BYTES)
        save(STRING_KEY, Value.newBuilder().setStringValue(expected).build())
        val executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)

        try {
            // WHEN they all read it at once, so the streams interleave on the single socket
            val readers = List(CONCURRENT_CALLERS) {
                Callable {
                    List(CALLS_PER_CALLER) { get(STRING_KEY).stringValue }
                }
            }
            val results = executor.invokeAll(readers).flatMap { it.get() }

            // THEN every call succeeds with the full value
            assertThat(results)
                .hasSize(CONCURRENT_CALLERS * CALLS_PER_CALLER)
                .containsOnly(expected)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun shouldFailWithUnavailableWhenTheSocketDoesNotExist() {
        // GIVEN a channel pointed at a path nothing listens on
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missingSocketPath = File(context.cacheDir, "missing-libwallet.sock").absolutePath
        val orphanChannel = GrpcChannelFactory.create(missingSocketPath)

        try {
            // WHEN a call is made
            val stub = WalletServiceGrpc.newBlockingStub(orphanChannel)
                .withDeadlineAfter(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // THEN it fails fast with UNAVAILABLE instead of hanging until the deadline
            val thrown = catchThrowable {
                stub.get(GetRequest.newBuilder().setKey(STRING_KEY).build())
            }
            assertThat(thrown).isInstanceOf(StatusRuntimeException::class.java)
            assertThat(Status.fromThrowable(thrown).code).isEqualTo(Status.Code.UNAVAILABLE)
        } finally {
            orphanChannel.shutdownNow()
        }
    }

    private fun stub(): WalletServiceBlockingStub {
        return blockingStub.withDeadlineAfter(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun get(key: String): Value {
        return stub().get(GetRequest.newBuilder().setKey(key).build()).value
    }

    private fun save(key: String, value: Value) {
        stub().save(SaveRequest.newBuilder().setKey(key).setValue(value).build())
    }

    /**
     * Build a deterministic string of the given size whose content depends on both the size and
     * the position, so a dropped, duplicated or reordered chunk changes the result.
     */
    private fun payload(size: Int): String {
        val builder = StringBuilder(size)
        var state = size.toLong()

        repeat(size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            builder.append(ALPHABET[((state ushr 33) % ALPHABET.length).toInt()])
        }

        return builder.toString()
    }
}
