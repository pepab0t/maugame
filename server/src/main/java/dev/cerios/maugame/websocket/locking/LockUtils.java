package dev.cerios.maugame.websocket.locking;

import lombok.experimental.UtilityClass;

import java.util.concurrent.locks.Lock;

@UtilityClass
public class LockUtils {

    public static void runLocked(Lock lock, Runnable runnable) {
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public static <R, E extends Throwable> R runLocked(Lock lock, CheckedTask<R, E> task) throws E {
        try {
            lock.lock();
            return task.run();
        } finally {
            lock.unlock();
        }
    }

    public static Runnable wrapLock(Lock lock, Runnable runnable) {
        return () -> {
            try {
                lock.lock();
                runnable.run();
            } finally {
                lock.unlock();
            }
        };
    }

    public interface CheckedTask<R, E extends Throwable> {
        R run() throws E;
    }
}
