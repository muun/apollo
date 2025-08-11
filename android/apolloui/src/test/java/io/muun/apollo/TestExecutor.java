package io.muun.apollo;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A sequential single-threaded Executor that can be paused, resumed and waited upon.
 */
public class TestExecutor implements Executor {

    private volatile int pendingTasks = 0;
    private volatile boolean isRunning = true;

    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    public synchronized void pause() {
        isRunning = false;
    }

    public synchronized void resume() {
        isRunning = true;
        notify();
    }

    @Override
    public synchronized void execute(@NonNull Runnable command) {
        pendingTasks++;

        singleThreadExecutor.execute(() -> {
            waitUntilRunning();

            try {
                command.run();
            } finally {
                synchronized (this) {
                    pendingTasks--;
                    notify();
                }
            }
        });
    }

    public synchronized void waitUntilFinished() {
        while (pendingTasks > 0) {
            waitUponThis();
        }
    }

    private synchronized void waitUntilRunning() {
        while (!isRunning) {
            waitUponThis();
        }
    }

    private void waitUponThis() {
        try {
            wait();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
