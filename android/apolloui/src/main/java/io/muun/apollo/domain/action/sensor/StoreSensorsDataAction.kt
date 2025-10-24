package io.muun.apollo.domain.action.sensor

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.model.SensorEventBatch
import io.muun.apollo.domain.model.SensorEvent
import io.muun.apollo.presentation.ui.nfc.events.ISensorEvent
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.ReplaySubject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val BATCHING_TIME_INTERVAL_SECONDS = 3L
private const val MAX_BATCH_SIZE = 500
private const val MAX_BATCH_QUEUE_SIZE = MAX_BATCH_SIZE * 20

/**
 * Collects and batches sensor event data over time, sending it to the backend via [HoustonClient].
 *
 * Extends [BaseAsyncAction1] to asynchronously handle individual [ISensorEvent] inputs.
 * Batches events every [BATCHING_TIME_INTERVAL_SECONDS] or after [MAX_BATCH_SIZE] events, whichever comes first.
 * In case of network failure, events are requeued for future retries.
 */
class StoreSensorsDataAction @Inject constructor(
    private val houstonClient: HoustonClient,
) : BaseAsyncAction1<ISensorEvent, Void>() {

    /**
     * Subject used to queue and buffer incoming sensor event data before batch transmission.
     * If the queue is full we'll drop the event surplus.
     */
    private val sensorDataSubject = ReplaySubject
        .createWithSize<SensorEvent>(MAX_BATCH_QUEUE_SIZE).toSerialized()

    /**
     * Initializes the batching mechanism, which observes incoming events and sends them to the backend
     * in batches at regular intervals or when the batch size limit is reached.
     *
     * Failed transmissions are logged and requeued for retry. If sensorDataSubject queue gets full
     * we'll drop the event surplus.
     */
    init {
        sensorDataSubject
            .buffer(BATCHING_TIME_INTERVAL_SECONDS, TimeUnit.SECONDS, MAX_BATCH_SIZE)
            .filter { it.isNotEmpty() }
            .observeOn(Schedulers.io())
            .subscribe({ eventList ->
                val batch = SensorEventBatch(
                    events = eventList,
                )
                houstonClient.saveSensorEventBatch(batch)
                    .subscribe(
                        { /* success, nothing to do */ },
                        { error ->
                            Timber.e(error, "Permanent failure saving sensor data, requeueing")
                            eventList.forEach { sensorDataSubject.onNext(it) }
                        }
                    )
            }, { error ->
                Timber.e(error, "Unexpected error in batching stream")
            })
    }

    /**
     * Handles an incoming [ISensorEvent] by converting it to [SensorEvent] and queuing it for batching.
     *
     * @param sensorEvent The sensor event to process and queue.
     * @return An [Observable] that completes immediately after queuing the event.
     */
    override fun action(
        sensorEvent: ISensorEvent,
    ): Observable<Void> {
        sensorDataSubject.onNext(
            sensorEvent.handle()
        )

        return Observable.create { it.onCompleted() }
    }
}