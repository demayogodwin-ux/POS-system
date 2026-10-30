package com.pos.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.concurrent.Task;

public final class Background {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "pos-db-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private Background() {
    }

    public static void run(Task<?> task) {
        EXECUTOR.execute(task);
    }
}
