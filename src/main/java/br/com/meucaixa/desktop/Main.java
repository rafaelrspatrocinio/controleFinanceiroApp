package br.com.meucaixa.desktop;

import br.com.meucaixa.desktop.ui.MainFrame;
import br.com.meucaixa.persistence.file.LedgerStore;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("Meu Caixa Java precisa de uma área de trabalho gráfica para abrir.");
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    UIManager.put("Button.focus", UIManager.getColor("Button.background"));
                }
                new MainFrame(new LedgerStore()).setVisible(true);
            }
        });
    }
}
