package lk.metrolink.journey.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lk.metrolink.journey.repository.UnitOfWork;
import org.junit.After;
import org.junit.Test;

/** The unit of work as the console shows it (rule L7): begin, then commit or rollback; nested ones only at debug. */
public class LoggedUnitOfWorkTest {

    /** A unit of work that just runs the work, so only the decorator's own lines are tested. */
    private static final UnitOfWork PLAIN = new UnitOfWork() {
        @Override
        public <T> T run(Supplier<T> work) {
            return work.get();
        }
    };

    private final LoggedUnitOfWork uow = new LoggedUnitOfWork(PLAIN);

    @After
    public void quiet() {
        Log.reset();
    }

    @Test
    public void anOrdinaryUnitOfWorkBeginsAndCommits() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            String result = uow.run(() -> "answer");

            assertEquals("answer", result);
            assertEquals(2, capture.lines(Log.UOW).size());
            capture.assertInOrder("UOW      begin:", "UOW      commit: changes kept");
        }
    }

    @Test
    public void theWorkRunsBetweenTheTwoLines() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            uow.run(() -> {
                Log.info(Log.SERVICE, "the work");
                return null;
            });

            capture.assertInOrder("begin:", "the work", "commit:");
        }
    }

    @Test
    public void aUnitOfWorkInsideAnotherJoinsItAndSaysSoOnlyAtDebug() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            uow.run(() -> uow.run(() -> uow.run(() -> "deep")));

            assertEquals("one begin and one commit, however deep", 2, capture.lines(Log.UOW).size());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            uow.run(() -> uow.run(() -> uow.run(() -> "deep")));

            assertEquals(4, capture.lines(Log.UOW).size());
            capture.assertInOrder("begin:", "join: a nested unit of work (depth 2)", "join: a nested unit of work (depth 3)",
                    "commit:");
        }
    }

    @Test
    public void anExceptionIsAnnouncedAsARollbackAndRethrownUnchanged() {
        IllegalStateException boom = new IllegalStateException("boom");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                uow.run(() -> {
                    throw boom;
                });
                fail("expected the exception");
            } catch (IllegalStateException e) {
                assertSame(boom, e);
            }

            capture.assertInOrder("begin:", "rollback: IllegalStateException: boom - every change in this unit of work is undone");
            assertTrue(capture.text(), !capture.text().contains("commit:"));
        }
    }

    @Test
    public void anErrorIsAlsoARollback() {
        AssertionError error = new AssertionError("broken");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                uow.run(() -> {
                    throw error;
                });
                fail("expected the error");
            } catch (AssertionError e) {
                assertSame(error, e);
            }

            capture.assertInOrder("rollback: AssertionError: broken");
        }
    }

    @Test
    public void anExceptionWithoutAMessageIsStillNamed() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                uow.run(() -> {
                    throw new IllegalStateException();
                });
            } catch (IllegalStateException expected) {
                // fine
            }

            assertTrue(capture.text(), capture.text().contains("rollback: IllegalStateException - every change"));
        }
    }

    @Test
    public void aFailureInsideANestedUnitOfWorkIsTheOuterOnesRollback() {
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            try {
                uow.run(() -> uow.run(() -> {
                    throw new IllegalStateException("deep failure");
                }));
            } catch (IllegalStateException expected) {
                // fine
            }

            capture.assertInOrder("begin:", "join:", "nested unit of work failed: IllegalStateException: deep failure",
                    "rollback: IllegalStateException: deep failure");
        }
    }

    @Test
    public void afterAFailureTheNextUnitOfWorkBeginsAgainInsteadOfJoining() {
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            try {
                uow.run(() -> {
                    throw new IllegalStateException("first fails");
                });
            } catch (IllegalStateException expected) {
                // fine
            }
            uow.run(() -> "second works");

            assertEquals(2, capture.lines().stream().filter(l -> l.contains("UOW      begin:")).count());
            assertEquals(0, capture.lines().stream().filter(l -> l.contains("join:")).count());
        }
    }

    @Test
    public void anOuterUnitThatCatchesAFailureOfANestedOneStillCommits() {
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            uow.run(() -> {
                try {
                    uow.run(() -> {
                        throw new IllegalStateException("handled");
                    });
                } catch (IllegalStateException expected) {
                    // the outer unit carries on
                }
                return uow.run(() -> "still in the outer one");
            });

            capture.assertInOrder("begin:", "nested unit of work failed", "join: a nested unit of work (depth 2)", "commit:");
            assertEquals("the depth was put back after the failure", 0,
                    capture.lines().stream().filter(l -> l.contains("(depth 3)")).count());
        }
    }

    @Test
    public void eachThreadHasItsOwnDepth() throws Exception {
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            CountDownLatch bothInside = new CountDownLatch(2);
            AtomicReference<Throwable> problem = new AtomicReference<>();
            Runnable work = () -> {
                try {
                    uow.run(() -> {
                        bothInside.countDown();
                        try {
                            bothInside.await();
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        return null;
                    });
                } catch (Throwable t) {
                    problem.set(t);
                }
            };
            Thread a = new Thread(work);
            Thread b = new Thread(work);
            a.start();
            b.start();
            a.join();
            b.join();

            assertEquals(null, problem.get());
            assertEquals("two outermost units, not one that the other joined", 2,
                    capture.lines().stream().filter(l -> l.contains("UOW      begin:")).count());
            assertEquals(0, capture.lines().stream().filter(l -> l.contains("join:")).count());
        }
    }

    @Test
    public void withTheLogOffItStillRunsTheWorkAndKeepsTheException() {
        assertEquals(Log.Level.OFF, Log.level());

        assertEquals("fine", uow.run(() -> "fine"));
        try {
            uow.run(() -> {
                throw new IllegalArgumentException("kept");
            });
            fail("expected the exception");
        } catch (IllegalArgumentException e) {
            assertEquals("kept", e.getMessage());
        }
    }

    @Test
    public void aReadOnlyUnitOfWorkShowsItsBeginAndCommitOnlyAtDebug() {
        LoggedUnitOfWork reading = LoggedUnitOfWork.readOnly(PLAIN);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals("list", reading.run(() -> "list"));

            assertEquals("a screen asking for a list is not news", 0, capture.lines().size());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            reading.run(() -> "list");

            assertEquals(2, capture.lines(Log.UOW).size());
            capture.assertInOrder("DEBUG -     UOW      begin:", "DEBUG -     UOW      commit:");
        }
    }

    @Test
    public void aReadOnlyUnitOfWorkThatFailsIsStillAnnouncedAtInfo() {
        LoggedUnitOfWork reading = LoggedUnitOfWork.readOnly(PLAIN);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                reading.run(() -> {
                    throw new IllegalStateException("no such passenger");
                });
                fail("expected the exception");
            } catch (IllegalStateException expected) {
                assertEquals("no such passenger", expected.getMessage());
            }

            assertEquals(1, capture.lines().size());
            capture.assertInOrder("INFO  -     UOW      rollback: IllegalStateException: no such passenger");
        }
    }

    @Test
    public void aReadOnlyUnitInsideAWritingOneJoinsItBecauseTheyShareOneDepth() {
        LoggedUnitOfWork reading = LoggedUnitOfWork.readOnly(PLAIN);
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            uow.run(() -> reading.run(() -> "inside"));

            assertEquals(1, capture.lines().stream().filter(l -> l.contains("UOW      begin:")).count());
            assertEquals(1, capture.lines().stream().filter(l -> l.contains("UOW      commit:")).count());
            capture.assertInOrder("INFO  -     UOW      begin:", "join: a nested unit of work (depth 2)",
                    "INFO  -     UOW      commit:");
        }
    }

    @Test
    public void itNeverRunsTheWorkMoreThanOnce() {
        int[] runs = new int[1];

        uow.run(() -> ++runs[0]);

        assertEquals(1, runs[0]);
    }
}
