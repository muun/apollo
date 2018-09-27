package io.muun.apollo.data.os.execution;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
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

    private static final int INITIAL_POOL_SIZE = 3;

    private static final int MAX_POOL_SIZE = 5;

    private static final int KEEP_ALIVE_SECONDS = 10;

    private final ThreadPoolExecutor threadPoolExecutor;

    /**
     * Constructor.
     */
    @Inject
    public JobExecutor() {

        threadPoolExecutor = new ThreadPoolExecutor(
                INITIAL_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new JobThreadFactory()
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
