
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    static class HighlightChangeListener implements DocumentListener {
        ExternalHighlighter highlighter;
        StyledDocument doc;

        HighlightChangeListener() {
            highlighter = new ExternalHighlighter(
                    () -> SwingUtilities.invokeLater(this::processResponses));
        }

        public void insertUpdate(DocumentEvent e) {
            if (doc == null) {
                doc = (StyledDocument) e.getDocument();
            }
            assert doc == e.getDocument();

            int offset = e.getOffset();
            String text;
            try {
                text = e.getDocument().getText(offset, e.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
                return;
            }
            highlighter.insert(e.getOffset(), text);
        }

        public void removeUpdate(DocumentEvent e) {
            highlighter.remove(e.getOffset(), e.getLength());
        }
        public void changedUpdate(DocumentEvent e) { }

        void processResponses() {
            ExternalHighlighter.Highlight h;
            while (null != (h = highlighter.dequeue())) {
                SimpleAttributeSet attrSet = new SimpleAttributeSet();
                StyleConstants.setBackground(attrSet, h.color);
                StyleConstants.setForeground(attrSet, Color.WHITE);
                doc.setCharacterAttributes(h.pos, 1, attrSet, false);
            }
        }
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("Highlight Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextPane textPane = new JTextPane();
        StyledDocument doc = textPane.getStyledDocument();
        doc.addDocumentListener(new HighlightChangeListener());

        JScrollPane scrollPane = new JScrollPane(textPane);
        frame.add(scrollPane);

        frame.setPreferredSize(new Dimension(800, 600));
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        Logger logger = Logger.getGlobal();
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);
        logger.setLevel(Level.ALL);
        SwingUtilities.invokeLater(Main::createAndShow);
    }
}
