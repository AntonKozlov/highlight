import name.anton.highlight.ExternalHighlighter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

class ExternalHighlighterTest {
    private ExternalHighlighter hl;

    private static final boolean DEBUG = false;

    @BeforeAll
    static void beforeAll() {
        Logger logger = Logger.getGlobal();
        if (DEBUG) {
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
            logger.setLevel(Level.ALL);
        }
    }

    @BeforeEach
    void beforeEach() {
        hl = new ExternalHighlighter(null, "highlight_test", 2_000);
    }

    @AfterEach
    void afterEach() {
        assertThrows(TimeoutException.class, () -> assertDeque(0, Color.BLACK, 300));
        try {
            hl.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class TimeoutException extends Exception {
    }

    void assertDeque(int pos, Color c, int timeoutMs) throws TimeoutException {
        ExternalHighlighter.Highlight h = hl.dequeue();
        long startTs = System.nanoTime();
        while (h == null && (((System.nanoTime() - startTs) / 1_000_000) < timeoutMs)) {
            try {
                Thread.sleep(3);
            } catch (InterruptedException ignored) {
            }
            h = hl.dequeue();
        }
        if (h == null) {
            throw new TimeoutException();
        }
        assertEquals(pos, h.pos);
        assertEquals(c, h.color);

    }

    void assertDeque(int pos, Color c) throws TimeoutException {
        assertDeque(pos, c, 3_000);
    }

    @Test
    void sanity() throws TimeoutException {
        hl.insert(0, "R");
        hl.insert(1, "G");
        hl.insert(2, "B");

        assertDeque(0, Color.RED);
        assertDeque(1, Color.GREEN);
        assertDeque(2, Color.BLUE);

        hl.insert(6, "RGBRGB");

        assertDeque(6, Color.RED);
        assertDeque(7, Color.GREEN);
        assertDeque(8, Color.BLUE);
        assertDeque(9, Color.RED);
        assertDeque(10, Color.GREEN);
        assertDeque(11, Color.BLUE);
    }

    @Test
    void ignoredCrash() throws TimeoutException {
        hl.insert(0, "C");
        assertDeque(0, Color.GRAY);
    }

    @Test
    void unrrestoreableCrash() {
        assertThrows(TimeoutException.class, () -> {
            hl.insert(1, "TC");
            assertDeque(0, Color.GRAY);
        });
    }

    @Test
    void restoreableCrash() throws TimeoutException {
        hl.insert(0, "T");
        assertDeque(0, Color.GRAY);
        hl.insert(1, "C");
        assertDeque(1, Color.GRAY);
    }

    @Test
    void restoreableSleep() throws TimeoutException {
        hl.insert(0, "TS");
        assertDeque(0, Color.GRAY);
        assertDeque(1, Color.GRAY);
    }

    static class NotifyRunnable implements Runnable {
        int cnt = 0;

        @Override
        public synchronized void run() {
            ++cnt;
            notify();
        }
    }

    @Test
    void notifyCalled() throws TimeoutException, InterruptedException {
        hl.shutdown();

        NotifyRunnable r = new NotifyRunnable();
        hl = new ExternalHighlighter(r, "highlight_test", 2_000);
        synchronized (r) {
            hl.insert(0, "a");
            try {
                r.wait(3_000);
            } catch (InterruptedException ignored) {
            }
            assertTrue(0 < r.cnt);
        }
        assertDeque(0, Color.BLACK, 0);
    }

    @Test
    void randomTyping() throws TimeoutException {
        hl.insert(0, "R");
        hl.insert(0, "G");
        hl.insert(0, "B");

        assertDeque(2, Color.RED);
        assertDeque(1, Color.GREEN);
        assertDeque(0, Color.BLUE);

        hl.insert(5, "R");
        hl.insert(3, "G");
        hl.insert(1, "B");

        assertDeque(7, Color.RED);
        assertDeque(4, Color.GREEN);
        assertDeque(1, Color.BLUE);
    }

    @Test
    void deleteTyped() throws TimeoutException {
        hl.insert(0, "R");
        hl.remove(0, 1);
        hl.insert(0, "G");
        hl.remove(0, 1);
        hl.insert(0, "B");

        assertDeque(0, Color.BLUE);
    }

    @Test
    void BigText() throws TimeoutException {
        final int n = 16 * 1024;
        String s = Stream.generate(() -> "RB").limit(n).collect(joining());
        hl.insert(0, s);
        for (int i = 0; i < n; ++i) {
            assertDeque(2 * i, Color.RED);
            assertDeque(2 * i + 1, Color.BLUE);
        }
    }
}