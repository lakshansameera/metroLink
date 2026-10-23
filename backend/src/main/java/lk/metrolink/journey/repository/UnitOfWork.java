package lk.metrolink.journey.repository;

import java.util.function.Supplier;


public interface UnitOfWork {

     <T> T run(Supplier<T> work);

    default void execute(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }
}