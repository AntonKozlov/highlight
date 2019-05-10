import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ExternalHighlighterTest {
    private ExternalHighlighter hl;

    @BeforeAll
    static void beforeAll() {
        Logger logger = Logger.getGlobal();
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);
        logger.setLevel(Level.ALL);
    }

    @BeforeEach
    void beforeEach() {
        hl = new ExternalHighlighter(null, "highlight_test", 2_000);
    }

    @AfterEach
    void afterEach() {
        try {
            hl.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class TimeoutException extends Exception {}

    void assertDeque(int pos, Color c) throws TimeoutException {
        ExternalHighlighter.Highlight h = hl.dequeue();
        long startTs = System.nanoTime();
        while (h == null && (((System.nanoTime() - startTs) / 1_000_000_000) < 10)) {
            try {
                Thread.sleep(100);
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

    @Test
    void sanity() throws TimeoutException {
        hl.insert(0, "R");
        hl.insert(1, "G");
        hl.insert(2, "B");

        assertDeque(0, Color.RED);
        assertDeque(1, Color.GREEN);
        assertDeque(2, Color.BLUE);
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
        hl.insert(1, "C");
        assertDeque(0, Color.GRAY);
        assertDeque(1, Color.GRAY);
    }

    @Test
    void restoreableSleep() throws TimeoutException {
        hl.insert(0, "TS");
        assertDeque(0, Color.GRAY);
    }
}