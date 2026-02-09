package io.muun.apollo.presentation.app.startup

import org.junit.Assert
import org.junit.Test
import rx.Completable
import rx.observers.TestSubscriber
import rx.schedulers.TestScheduler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class AppStartupInitializerTest {

    private val log = CopyOnWriteArrayList<String>()

    private fun log(msg: String) {
        synchronized(log) { log += msg }
        println(msg)
    }

    private fun delayedInit(name: String, delayMs: Long, scheduler: TestScheduler): Completable =
        Completable.timer(delayMs, TimeUnit.MILLISECONDS, scheduler)
            .doOnSubscribe { log("[$name] init") }
            .doOnCompleted { log("[$name] finish") }

    inner class Init1(val scheduler: TestScheduler) : Initializer {
        override fun init(): Completable = delayedInit(Init1::class.java.simpleName, 1000, scheduler)
    }

    inner class Init2(val scheduler: TestScheduler) : Initializer {
        override fun dependencies() = setOf(Init1::class.java)
        override fun init(): Completable = delayedInit(Init2::class.java.simpleName, 2000, scheduler)
    }

    inner class Init3(val scheduler: TestScheduler) : Initializer {
        override fun init(): Completable = delayedInit(Init3::class.java.simpleName, 3000, scheduler)
    }

    inner class Init4(val scheduler: TestScheduler) : Initializer {
        override fun dependencies() = setOf(Init2::class.java)
        override fun init(): Completable = delayedInit(Init4::class.java.simpleName, 4000, scheduler)
    }

    inner class Init5(val scheduler: TestScheduler) : Initializer {
        override fun dependencies() = setOf(Init3::class.java, Init4::class.java)
        override fun init(): Completable = delayedInit(Init5::class.java.simpleName, 5000, scheduler)
    }

    @Test
    fun shouldParallelizeWorkWheneverPossible() {
        val scheduler = TestScheduler()

        val initializers = mapOf(
            Init1::class.java to Init1(scheduler), // 1000millis, dependencies: none
            Init2::class.java to Init2(scheduler), // 2000millis, dependencies: Init1
            Init3::class.java to Init3(scheduler), // 3000millis, dependencies: none
            Init4::class.java to Init4(scheduler), // 4000millis, dependencies: Init2
            Init5::class.java to Init5(scheduler), // 5000millis, dependencies: Init3, Init4
        )

        val testSubscriber = TestSubscriber<Void>()

        AppStartupInitializer(initializers).init()
            .subscribe(testSubscriber)

        // Advance virtual time sufficiently to complete all
        scheduler.advanceTimeBy(20, TimeUnit.SECONDS)

        // Asserts that initialization chain completed
        testSubscriber.assertCompleted()

        // Asserts that all initializers ran and ran exactly once
        initializers.forEach { (clazz, _) ->
            val count = log.count { it.contains("[${clazz.simpleName}] init") }
            Assert.assertTrue(count == 1)
        }

        // Asserts execution order
        Assert.assertTrue(indexOfFinish(Init1::class.java.simpleName) < indexOfInit(Init2::class.java.simpleName))
        Assert.assertTrue(indexOfFinish(Init2::class.java.simpleName) < indexOfInit(Init4::class.java.simpleName))
        Assert.assertTrue(indexOfFinish(Init3::class.java.simpleName) < indexOfInit(Init5::class.java.simpleName))
        Assert.assertTrue(indexOfFinish(Init4::class.java.simpleName) < indexOfInit(Init5::class.java.simpleName))

        // Asserts parallel execution
        Assert.assertTrue(
            "${Init1::class.java.simpleName} and ${Init2::class.java.simpleName} should init concurrently",
            indexOfInit(Init3::class.java.simpleName) == indexOfInit(Init1::class.java.simpleName) + 1 ||
                indexOfInit(Init1::class.java.simpleName) == indexOfInit(Init3::class.java.simpleName) + 1
        )

        // Asserts series execution
        Assert.assertTrue(
            "${Init2::class.java.simpleName} should start immediately after ${Init1::class.java.simpleName} finishes",
            indexOfInit(Init2::class.java.simpleName) > indexOfFinish(Init1::class.java.simpleName) &&
                indexOfInit(Init2::class.java.simpleName) < indexOfFinish(Init3::class.java.simpleName)
        )
    }

    fun indexOfFinish(name: String) = log.indexOfFirst { it.contains("[$name] finish") }
    fun indexOfInit(name: String) = log.indexOfFirst { it.contains("[$name] init") }
}
