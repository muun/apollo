package io.muun.apollo.data.async.tasks

import android.content.Context
import androidx.work.WorkerParameters
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.data.external.DataComponentProvider
import org.junit.Assert.assertNull
import org.junit.Test

class MuunWorkerFactoryTest {

    private val provider = mockk<DataComponentProvider> {
        every { dataComponent } returns mockk(relaxed = true)
    }

    private val factory = MuunWorkerFactory(provider)

    @Test
    fun returnsNullForAWorkerClassThatNoLongerExists() {
        val worker = factory.createWorker(
            mockk<Context>(),
            "io.muun.apollo.osmodules.removed.RemovedWorker",
            mockk<WorkerParameters>()
        )

        assertNull(worker)
    }
}
