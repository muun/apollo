package io.muun.apollo.data.os.execution;

import androidx.annotation.NonNull;
import timber.log.Timber;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

/**
 * Thread pool executor used by {ExecutionTransformerFactory} to take as much work as possible off
 * the UI thread.
 */
@Singleton
public class JobExecutor implements Executor {

    // The maximum amount of threads in the pool under normal circumstances
    private static final int CORE_POOL_SIZE = 5; // Should we calc (number of cores * 2)?

    // The HARD maximum of threads, to which threadPool can grow ONLY WHEN workQueue is FULL
    private static final int MAX_POOL_SIZE = 10;

    /* The maximum queue size, when reached a RejectedExecutionHandler is thrown */
    private static final int QUEUE_SIZE = 5; // capacity-bound queue needed for pool resizing

    // When over CORE_POOL_SIZE, this is the time to wait before terminating idle threads.
    private static final int KEEP_ALIVE_SECONDS = 10;

    private final ThreadPoolExecutor threadPoolExecutor;

    /**
     * Constructor.
     */
    @Inject
    public JobExecutor() {
        // See https://stackoverflow.com/a/33752839/901465 or ThreadPoolExecutor javadoc for why
        // a capacity-bound collection is needed for pool resizing to work.
        threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE), // avoid infinite task scheduling
                new JobThreadFactory(),
                getRejectedExecutionHandler()
        );
    }

    /**
     * Simple discard rejected task policy, logging it so we can be notified about it.
     */
    @NonNull
    private RejectedExecutionHandler getRejectedExecutionHandler() {
        // TODO log more information about the rejected task? About the tasks in the workQueue?
        return (runnable, executor) -> Timber.e(
                new RejectedExecutionException(
                        String.format("Task  %s rejected from %s. %s",
                                runnable,
                                executor,
                                getExtraLogginData(executor)
                        )
                )
        );
    }

    private String getExtraLogginData(ThreadPoolExecutor executor) {
        return String.format(
                ". TaskCount:%s. CompletedCount:%s. ActiveCount:%s. PoolSize:%s. WorkQueueSize:%s",
                executor.getTaskCount(),
                executor.getCompletedTaskCount(),
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size()
        );
    }

    @Override
    public void execute(@NotNull Runnable runnable) {

        threadPoolExecutor.execute(runnable);
    }

    private static class JobThreadFactory implements ThreadFactory {

        private static final String THREAD_NAME = "android_";

        private int counter = 0;

        @Override
        public Thread newThread(@NotNull Runnable runnable) {

            return new Thread(runnable, THREAD_NAME + counter++);
        }
    }
}
