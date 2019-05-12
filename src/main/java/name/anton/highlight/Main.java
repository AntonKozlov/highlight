
package name.anton.highlight;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;

class Main {
    static class HighlightChangeListener implements DocumentListener {
        final ExternalHighlighter highlighter;
        final JTextPane textPane;
        StyledDocument doc;

        HighlightChangeListener(JTextPane textPane) {
            this.textPane = textPane;
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
                StyleConstants.setForeground(attrSet, h.color);
                doc.setCharacterAttributes(h.pos, 1, attrSet, false);
                textPane.getInputAttributes().addAttributes(attrSet);
            }
        }
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("Highlight Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextPane textPane = new JTextPane();
        StyledDocument doc = textPane.getStyledDocument();
        doc.addDocumentListener(new HighlightChangeListener(textPane));

        JScrollPane scrollPane = new JScrollPane(textPane);
        frame.add(scrollPane);

        frame.setPreferredSize(new Dimension(800, 600));
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShow);
    }
}
