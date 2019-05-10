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
        int from;
        int off;

        Change(int from) {
            this.from = from;
        }

        int getFrom() {
            return from + off;
        }

        boolean incFrom() {
            ++off;
            return getLen() <= off;
        }

        abstract int getLen();

        abstract  int adjustPos(int pos);
    }

    class Insert extends Change {
        String text;

        Insert(int from, String text) {
            super(from);
            this.text = text;
        }

        String getText() {
            if (text == null) {
                throw new UnsupportedOperationException();
            }
            return text;
        }

        int getLen() {
            return text.length();
        }

        int adjustPos(int pos) {
            if (from <= pos) {
                return pos + getLen();
            }
            return pos;
        }
    }

    class Remove extends Change {
        int len;

        Remove(int from, int len) {
            super(from);
            this.len = len;
        }

        int getLen() {
            return len;
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
        Color color;
        int pos;

        Highlight(int pos, Color color) {
            this.pos = pos;
            this.color = color;
        }
    }

    static class ExitException extends Exception {
    }

    class WatcherEntry implements Runnable {
        private final Logger logger = Logger.getGlobal();

        WatcherEntry(Runnable notify) {
            this.notify = notify;
        }

        Runtime runtime = Runtime.getRuntime();

        Runnable notify;
        Process process;
        InputStream is;
        OutputStream os;

        private void handleExitRequest() throws ExitException {
            if (exiting) {
                throw new ExitException();
            }
        }

        private void restartProcess() {
            if (process != null) {
                process.destroy();
            }
            try {
                process = runtime.exec("./csrc/" + cmdid);
                is = process.getInputStream();
                os = process.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private Insert getInsert() throws ExitException {
            synchronized (extInQ) {
                while (true) {
                    handleExitRequest();
                    Insert c = extInQ.poll();
                    if (c != null) {
                        logger.finer("get insert");
                        return c;
                    }
                    try {
                        extInQ.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        private boolean doHighlight(byte[] oBytes, byte[] iBytes) throws IOException {
            logger.finer("send " + Arrays.toString(oBytes));
            os.write(oBytes);
            os.flush();

            int iLen = is.read(iBytes, 0, is.available());
            long startTs = System.nanoTime();
            while (iLen < iBytes.length && (System.nanoTime() - startTs < awaitMs * 1_000_000)) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
                iLen += is.read(iBytes, iLen, is.available());
            }
            return iLen == iBytes.length;
        }

        @Override
        public void run() {
            try {
                restartProcess();

                while (true) {
                    Insert c = getInsert();

                    final byte[] oBytes = c.getText().getBytes();
                    byte[] iBytes = new byte[oBytes.length * 3];

                    while (true) {
                        boolean read = false;
                        try {
                            handleExitRequest();
                            read = doHighlight(oBytes, iBytes);
                        } catch (IOException e) {
                            logger.finer(e.toString());
                        }
                        if (read) {
                            break;
                        }
                        restartProcess();
                    }

                    logger.finer("get " + Arrays.toString(iBytes));
                    synchronized (outQ) {
                        for (int i = 0; i < iBytes.length; i += 3) {
                            outQ.add(new Color(iBytes[i] & 0xff, iBytes[i + 1] & 0xff, iBytes[i + 2] & 0xff));
                        }

                        if (notify != null) {
                            notify.run();
                        }
                    }
                }
            } catch (ExitException e) {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    private final Logger logger = Logger.getGlobal();

    private static final int RETRY_INTERVAL_MS = 3;
    private final String cmdid;
    private final int awaitMs;


    private final Queue<Insert> extInQ;
    private final Queue<Change> inProgress;
    private final Queue<Color> outQ;

    private boolean exiting;
    private final Thread watcherThread;

    ExternalHighlighter(Runnable notify) {
        this(notify, "highlight", 5_500);
    }

    ExternalHighlighter(Runnable notify, String cmdid, int awaitMs) {
        this.cmdid = cmdid;
        this.awaitMs = awaitMs;

        watcherThread = new Thread(new WatcherEntry(notify));
        extInQ = new ArrayDeque<>();
        inProgress = new ArrayDeque<>();
        outQ = new ArrayDeque<>();

        watcherThread.start();
    }

    public void insert(int pos, String text) {
        Insert ins = new Insert(pos, text);
        inProgress.add(ins);
        synchronized (extInQ) {
            extInQ.add(ins);
            extInQ.notify();
        }
    }

    public void remove(int pos, int len) {
        inProgress.add(new Remove(pos, len));
    }

    public Highlight dequeue() {
        do {
            Color color;
            synchronized (outQ) {
                color = outQ.poll();
            }
            if (color == null) {
                return null;
            }

            Change m = inProgress.peek();
            while (m instanceof Remove) {
                inProgress.remove();
                m = inProgress.peek();
            }
            if (m == null) {
                return null;
            }

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
            if (0 <= mPos) {
                return new Highlight(mPos, color);
            }
        } while (true);
    }

    public void shutdown() throws InterruptedException {
        synchronized (extInQ) {
            exiting = true;
            extInQ.notify();
        }
        watcherThread.join();
    }
}
