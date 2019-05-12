
package name.anton.highlight;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Queue;
import java.util.logging.Logger;

public class ExternalHighlighter {
    abstract class Change {
        final int from;
        int off;
        Change(int from) {
            this.from = from;
        }
        int getFrom() {
            return from + off;
        }
        boolean incFrom() {
            ++off;
            return getLen() <= 0;
        }
        abstract int getLen();
        abstract  int adjustPos(int pos);
    }

    class Insert extends Change {
        final String text;
        Insert(int from, String text) {
            super(from);
            this.text = text;
        }
        String getText() {
            if (text == null) {
                throw new UnsupportedOperationException();
            }
            return text.substring(off);
        }
        int getLen() {
            return text.length() - off;
        }
        int adjustPos(int pos) {
            if (from <= pos) {
                return pos + getLen();
            }
            return pos;
        }
    }

    class Remove extends Change {
        final int len;
        Remove(int from, int len) {
            super(from);
            this.len = len;
        }
        int getLen() {
            return len - off;
        }
        int adjustPos(int pos) {
            if (from <= pos && pos < from + getLen()) {
                return -1;
            } else if (from + getLen() <= pos) {
                return pos - getLen();
            }
            return pos;
        }
    }

    public class Highlight {
        public final Color color;
        public final int pos;

        Highlight(int pos, Color color) {
            this.pos = pos;
            this.color = color;
        }
    }

    static class WatcherEntry implements Runnable {
        private static final Logger logger = Logger.getGlobal();
        private static final Runtime runtime = Runtime.getRuntime();
        private static final int INPUT_COLOR_N = 16;
        private static final int RETRY_INTERVAL_MS = 3;

        private final Runnable notify;
        private final Queue<Insert> extInQ;
        private final Queue<Color> outQ;
        private final String cmdPath;
        private int timeoutMs;

        private boolean running;
        private Process process;
        private InputStream is;
        private OutputStream os;

        WatcherEntry(Runnable notify, Queue<Color> outQ, String cmdPath, int timeoutMs) {
            this.notify = notify;
            this.outQ = outQ;
            this.timeoutMs = timeoutMs;
            this.cmdPath = cmdPath;

            this.extInQ = new ArrayDeque<>();
            this.running = true;
        }

        synchronized void setExiting() {
            running = false;
            this.notify();
        }

        private synchronized void sendInsert(Insert c) throws IOException {
            final byte[] oBytes = c.getText().getBytes();
            if (process != null) {
                os.write(oBytes);
                os.flush();
                logger.finer("send" + Arrays.toString(oBytes));
            }
        }

        synchronized void pushInsert(Insert c) {
            extInQ.add(c);
            this.notify();
            try {
                sendInsert(c);
            } catch (IOException ignored) {
            }
        }

        synchronized private void restartProcess() {
            logger.finer("restart");

            if (process != null) {
                process.destroy();
            }
            try {
                process = runtime.exec(cmdPath);
                is = process.getInputStream();
                os = process.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                for (Insert i : extInQ) {
                    sendInsert(i);
                }
            } catch (IOException ignored) {
            }
        }

        private int tryRead(byte[] iBytes) {
            try {
                return is.read(iBytes, 0, Math.min(is.available(), iBytes.length));
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public void run() {
            byte[] iBytes = new byte[INPUT_COLOR_N * 3];
            restartProcess();

            mainloop: while (running) {
                synchronized (this) {
                    if (extInQ.isEmpty()) {
                        try {
                            logger.finer("extQ empty");

                            this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        if (!running) {
                            break mainloop;
                        }
                    }
                }

                logger.finer("extQ nonEmpty");
                long startTs = System.nanoTime();
                int iLen = tryRead(iBytes);
                while (iLen == 0) {
                    while (iLen == 0 && (System.nanoTime() - startTs < timeoutMs * 1_000_000)) {
                        try {
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } catch (InterruptedException ignored) {
                        }
                        iLen = tryRead(iBytes);
                    }
                    if (!running) {
                        break mainloop;
                    }
                    startTs = System.nanoTime();
                    if (iLen == 0) {
                        restartProcess();
                    }
                }

                logger.finer("get " + Arrays.toString(Arrays.copyOfRange(iBytes, 0, iLen)));

                // with current wrapper implementation for highlight function, it's impossible to get partial
                // response. But it's just easier to treat it so.
                synchronized (this) {
                    for (int i = 0; i < iLen; i += 3) {
                        if (extInQ.peek().incFrom()) {
                            extInQ.remove();
                        }
                    }
                }
                synchronized (outQ) {
                    for (int i = 0; i < iLen; i += 3) {
                        outQ.add(new Color(iBytes[i] & 0xff, iBytes[i + 1] & 0xff, iBytes[i + 2] & 0xff));
                    }
                }

                if (notify != null) {
                    notify.run();
                }
            }

            if (process != null) {
                process.destroy();
            }
        }
    }

    private final Logger logger = Logger.getGlobal();

    private final String cmdid;
    private final int awaitMs;

    private final Queue<Change> inProgress;
    private final Queue<Color> outQ;

    private final Thread watcherThread;
    private final WatcherEntry watcherEntry;

    ExternalHighlighter(Runnable notify) {
        this(notify, "highlight", 5_500);
    }

    public ExternalHighlighter(Runnable notify, String cmdid, int timeoutMs) {
        this.cmdid = cmdid;
        this.awaitMs = timeoutMs;

        inProgress = new ArrayDeque<>();
        outQ = new ArrayDeque<>();

        watcherEntry = new WatcherEntry(notify, outQ, "./csrc/" + cmdid, timeoutMs);
        watcherThread = new Thread(watcherEntry);
        watcherThread.start();
    }

    // see `dequeue` comment
    public void insert(int pos, String text) {
        inProgress.add(new Insert(pos, text));
        watcherEntry.pushInsert(new Insert(pos, text));
    }

    // see `dequeue` comment
    public void remove(int pos, int len) {
        inProgress.add(new Remove(pos, len));
    }

    private Color getColor() {
        synchronized (outQ) {
            return outQ.poll();
        }
    }

    private Insert getInsert() {
        Change m = inProgress.peek();
        while (m instanceof Remove) {
            inProgress.remove();
            m = inProgress.peek();
        }
        return (Insert)m;
    }

    private int computeCurrentPos(Insert m) {
        int mPos = m.getFrom();
        logger.fine("deque1 " + mPos);
        Iterator<Change> ic = inProgress.iterator();
        ic.next();
        while (ic.hasNext()) {
            mPos = ic.next().adjustPos(mPos);
            if (mPos == -1) {
                break;
            }
        }

        if (m.incFrom()) {
            inProgress.remove();
        }

        logger.fine("deque2 " + mPos);
        return mPos;
    }

    // `dequeue` should be externally synchronized with `insert` and `remove`.
    // It computes position of highlighted character, that can be falsified by
    // inserting/removing before the position. So it's user responsibility to
    // assure that those functions are not called while this one is executed.
    public Highlight dequeue() {
        Color color = null;
        int pos = -1;

        do {
            color = getColor();
            if (color == null) {
                logger.fine("dequeue empty");
                return null;
            }

            Insert m = getInsert();
            if (m == null) {
                logger.fine("dequeue no Insert");
                continue;
            }

            pos = computeCurrentPos(m);
        } while (pos < 0);

        return new Highlight(pos, color);
    }

    public void shutdown() throws InterruptedException {
        watcherEntry.setExiting();
        watcherThread.join();
    }
}
