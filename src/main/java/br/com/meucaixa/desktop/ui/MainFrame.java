package br.com.meucaixa.desktop.ui;

import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.domain.service.FinanceService;
import br.com.meucaixa.persistence.file.LedgerStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public final class MainFrame extends JFrame {
    private static final Color GREEN = new Color(24, 122, 83);
    private static final Color RED = new Color(196, 77, 77);
    private static final Color INK = new Color(24, 35, 31);
    private static final Color MUTED = new Color(110, 123, 115);
    private static final Color SURFACE = Color.WHITE;
    private static final String[] CATEGORIES = {
        "Renda Fixa", "Renda Variável", "Custos Fixos", "Despesa", "Mercado", "Cartão",
        "Dívida", "Pessoal", "Educação", "Saúde", "Pet", "Lazer", "Manutenção", "Outros"
    };
    private final LedgerStore store;
    private final List<Transaction> transactions = new ArrayList<Transaction>();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel balanceValue = valueLabel(30);
    private final JLabel balanceTitle = new JLabel("SALDO ACUMULADO");
    private final JLabel incomeValue = valueLabel(22);
    private final JLabel expenseValue = valueLabel(22);
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel(" ");
    private YearMonth selectedMonth = YearMonth.now();
    private long openingBalanceCents;
    private boolean loading;

    public MainFrame(LedgerStore store) {
        super("Meu Caixa");
        this.store = store;
        tableModel = new DefaultTableModel(new Object[] {"Data", "Tipo", "Descrição", "Categoria", "Valor", "ID"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) { return column == 4 ? Long.class : String.class; }
        };
        table = new JTable(tableModel);
        configureWindow();
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(new Color(245, 247, 244));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 12, 22));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createContent(), BorderLayout.CENTER);
        root.add(createStatus(), BorderLayout.SOUTH);
        setContentPane(root);
        loadData();
        render();
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(860, 600));
        setSize(1050, 700);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 5));
        header.setOpaque(false);
        JLabel title = new JLabel("Meu Caixa");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Seu dinheiro, organizado num só lugar.");
        subtitle.setForeground(MUTED);
        JPanel heading = new JPanel(new BorderLayout(2, 3));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        heading.add(subtitle, BorderLayout.SOUTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);
        actions.add(button("＋ Novo lançamento", GREEN, Color.WHITE, this::showNewTransaction));
        actions.add(button("Ajustes", SURFACE, GREEN, this::showSettings));
        actions.add(button("Exportar cópia", SURFACE, INK, this::exportBackup));
        actions.add(button("Importar cópia", SURFACE, INK, this::importBackup));
        actions.add(button("Limpar todos", SURFACE, RED, this::clearAllTransactions));
        header.add(heading, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(createSummary(), BorderLayout.NORTH);
        content.add(createTransactions(), BorderLayout.CENTER);
        return content;
    }

    private JPanel createSummary() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setOpaque(false);
        JPanel monthControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        monthControls.setOpaque(false);
        JButton previous = button("‹", SURFACE, GREEN, () -> changeMonth(-1));
        JButton next = button("›", SURFACE, GREEN, () -> changeMonth(1));
        monthLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        monthLabel.setForeground(INK);
        monthLabel.setPreferredSize(new Dimension(200, 34));
        monthControls.add(previous);
        monthControls.add(monthLabel);
        monthControls.add(next);
        JPanel cards = new JPanel(new GridBagLayout());
        cards.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1;
        constraints.insets = new Insets(0, 0, 0, 10);
        JPanel balanceCard = summaryCard(balanceTitle, balanceValue, INK, new Color(184, 199, 191));
        JPanel incomeCard = summaryCard("ENTRADAS NO MÊS", incomeValue, SURFACE, GREEN);
        JPanel expenseCard = summaryCard("SAÍDAS NO MÊS", expenseValue, SURFACE, RED);
        constraints.gridx = 0;
        constraints.weightx = 1.45;
        cards.add(balanceCard, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        cards.add(incomeCard, constraints);
        constraints.gridx = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        cards.add(expenseCard, constraints);
        wrapper.add(monthControls, BorderLayout.NORTH);
        wrapper.add(cards, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel summaryCard(String heading, JLabel value, Color background, Color foreground) {
        return summaryCard(new JLabel(heading), value, background, foreground);
    }

    private JPanel summaryCard(JLabel label, JLabel value, Color background, Color foreground) {
        JPanel card = new JPanel(new BorderLayout(4, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(231, 235, 231)),
                BorderFactory.createEmptyBorder(14, 17, 14, 17)));
        card.setBackground(background);
        label.setForeground(background.equals(INK) ? new Color(184, 199, 191) : MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        value.setForeground(foreground);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTransactions() {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel label = new JLabel("Movimentos do mês");
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        label.setForeground(INK);
        JButton delete = button("Excluir selecionado", SURFACE, RED, this::deleteSelected);
        heading.add(label, BorderLayout.WEST);
        heading.add(delete, BorderLayout.EAST);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(360);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(140);
        table.getColumnModel().getColumn(5).setMinWidth(0);
        table.getColumnModel().getColumn(5).setMaxWidth(0);
        table.getColumnModel().getColumn(5).setPreferredWidth(0);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(231, 235, 231)));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatus() {
        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(false);
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        status.add(statusLabel, BorderLayout.WEST);
        return status;
    }

    private void loadData() {
        loading = true;
        try {
            transactions.clear();
            transactions.addAll(store.loadTransactions());
            openingBalanceCents = store.loadOpeningBalance();
            statusLabel.setText("Dados locais: " + store.getTransactionsFile());
        } catch (IOException exception) {
            showError("Não foi possível abrir seus dados locais.", exception);
            statusLabel.setText("Erro ao carregar dados locais.");
        } finally {
            loading = false;
        }
    }

    private void render() {
        if (loading) return;
        monthLabel.setText(selectedMonth.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", java.util.Locale.forLanguageTag("pt-BR"))));
        List<Transaction> rows = FinanceService.forMonth(transactions, selectedMonth);
        incomeValue.setText(formatMoney(FinanceService.incomeForMonth(transactions, selectedMonth)));
        expenseValue.setText(formatMoney(FinanceService.expensesForMonth(transactions, selectedMonth)));
        LocalDate balanceDate = FinanceService.balanceDateForMonth(selectedMonth, LocalDate.now());
        balanceTitle.setText("SALDO EM " + balanceDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        balanceValue.setText(formatMoney(FinanceService.balanceThroughDate(transactions, balanceDate, openingBalanceCents)));
        tableModel.setRowCount(0);
        for (Transaction transaction : rows) {
            long signedAmount = transaction.signedAmountCents();
            tableModel.addRow(new Object[] {
                transaction.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                transaction.getType().getLabel(),
                transaction.getDescription(),
                transaction.getCategory(),
                signedAmount,
                transaction.getId()
            });
        }
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
                JLabel cell = (JLabel) super.getTableCellRendererComponent(source, formatMoney(((Long) value).longValue()), selected, focused, row, column);
                cell.setHorizontalAlignment(SwingConstants.RIGHT);
                if (!selected) cell.setForeground(((Long) value).longValue() >= 0 ? GREEN : RED);
                return cell;
            }
        });
        repaint();
    }

    private void showNewTransaction() {
        final JComboBox<String> type = new JComboBox<String>(new String[] {"Despesa", "Receita"});
        final JComboBox<String> category = new JComboBox<String>(CATEGORIES);
        final JTextField amount = new JTextField(18);
        final JTextField description = new JTextField(22);
        final JTextField date = new JTextField(LocalDate.now().toString(), 12);
        final JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Tipo"), c);
        c.gridx = 1;
        form.add(type, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Valor (R$)"), c);
        c.gridx = 1;
        form.add(amount, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Descrição"), c);
        c.gridx = 1;
        form.add(description, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Data (AAAA-MM-DD)"), c);
        c.gridx = 1;
        form.add(date, c);
        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Categoria"), c);
        c.gridx = 1;
        form.add(category, c);
        type.addActionListener(event -> category.setSelectedItem("Receita".equals(type.getSelectedItem()) ? "Renda Variável" : "Despesa"));
        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Novo lançamento", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;
            try {
                long cents = parseMoney(amount.getText());
                LocalDate transactionDate = LocalDate.parse(date.getText().trim());
                Transaction transaction = new Transaction(FinanceService.nextId(transactions),
                        Transaction.Type.fromLabel((String) type.getSelectedItem()), cents,
                        (String) category.getSelectedItem(), description.getText(), transactionDate);
                List<Transaction> updated = new ArrayList<Transaction>(transactions);
                updated.add(transaction);
                if (saveTransactions(updated, "Lançamento salvo.")) {
                    selectedMonth = YearMonth.from(transactionDate);
                    render();
                    return;
                }
                return;
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage() == null ? "Confira os campos informados." : exception.getMessage(), "Dados inválidos", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void showSettings() {
        final JTextField opening = new JTextField(formatDecimal(openingBalanceCents), 18);
        JPanel panel = new JPanel(new BorderLayout(6, 10));
        panel.add(new JLabel("Saldo de referência anterior aos lançamentos (R$):"), BorderLayout.NORTH);
        panel.add(opening, BorderLayout.CENTER);
        panel.add(new JLabel(transactions.size() + " movimentos salvos neste computador."), BorderLayout.SOUTH);
        int result = JOptionPane.showConfirmDialog(this, panel, "Ajustes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try {
            long cents = parseMoney(opening.getText());
            if (cents < 0) throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
            store.saveOpeningBalance(cents);
            openingBalanceCents = cents;
            render();
            statusLabel.setText("Saldo inicial atualizado.");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Saldo inválido", JOptionPane.WARNING_MESSAGE);
        } catch (IOException exception) {
            showError("Não foi possível salvar o saldo inicial.", exception);
        }
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um movimento na lista para excluí-lo.", "Nenhum movimento selecionado", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        LocalDate selectedDate = LocalDate.parse((String) tableModel.getValueAt(modelRow, 0), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String description = (String) tableModel.getValueAt(modelRow, 2);
        String category = (String) tableModel.getValueAt(modelRow, 3);
        long signed = ((Long) tableModel.getValueAt(modelRow, 4)).longValue();
        Transaction.Type type = signed >= 0 ? Transaction.Type.INCOME : Transaction.Type.EXPENSE;
        long selectedId = ((Long) tableModel.getValueAt(modelRow, 5)).longValue();
        Transaction selected = null;
        for (Transaction transaction : transactions) {
            if (transaction.getId() == selectedId) {
                selected = transaction;
                break;
            }
        }
        if (selected == null) return;
        int confirmation = JOptionPane.showConfirmDialog(this, "Excluir “" + selected.getDescription() + "”?", "Confirmar exclusão", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) return;
        List<Transaction> updated = new ArrayList<Transaction>(transactions);
        updated.remove(selected);
        if (saveTransactions(updated, "Movimento excluído.")) render();
    }

    private void clearAllTransactions() {
        if (transactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Não há lançamentos para limpar.", "Lista vazia", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Isso excluirá permanentemente todos os " + transactions.size() + " lançamentos deste computador. O saldo inicial será mantido. Deseja continuar?",
                "Limpar todos os lançamentos", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) return;
        if (saveTransactions(new ArrayList<Transaction>(), "Todos os lançamentos foram removidos; o saldo inicial foi mantido.")) render();
    }

    private void changeMonth(int offset) {
        selectedMonth = selectedMonth.plusMonths(offset);
        render();
    }

    private void exportBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar cópia dos lançamentos");
        chooser.setSelectedFile(new File("meu-caixa-backup.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            LedgerStore.writeBackup(chooser.getSelectedFile().toPath(), transactions);
            statusLabel.setText("Cópia exportada: " + chooser.getSelectedFile().getName());
        } catch (IOException exception) {
            showError("Não foi possível exportar a cópia.", exception);
        }
    }

    private void importBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Importar cópia CSV");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        try {
            List<Transaction> imported = LedgerStore.readBackup(file);
            int confirmation = JOptionPane.showConfirmDialog(this,
                    "Importar esta cópia substituirá os " + transactions.size() + " movimentos atuais. Deseja continuar?",
                    "Confirmar importação", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmation != JOptionPane.YES_OPTION) return;
            if (saveTransactions(imported, "Cópia importada com sucesso.")) render();
        } catch (IOException exception) {
            showError("Não foi possível importar essa cópia.", exception);
        }
    }

    private boolean saveTransactions(List<Transaction> updated, String message) {
        try {
            store.saveTransactions(updated);
            transactions.clear();
            transactions.addAll(updated);
            statusLabel.setText(message);
            return true;
        } catch (IOException exception) {
            showError("Não foi possível salvar seus lançamentos.", exception);
            return false;
        }
    }

    private void showError(String title, Exception exception) {
        JOptionPane.showMessageDialog(this, title + "\n" + exception.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
    }

    private static JButton button(String text, Color background, Color foreground, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(225, 232, 226)), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JLabel valueLabel(int size) {
        JLabel label = new JLabel("R$ 0,00");
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        return label;
    }

    public static String formatMoney(long cents) {
        java.text.NumberFormat format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR"));
        return format.format(cents / 100.0);
    }

    public static long parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Informe um valor.");
        String normalized = value.trim().replace("R$", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(',', '.');
        if (!normalized.matches("-?\\d+(\\.\\d{1,2})?")) throw new IllegalArgumentException("Use um valor como 1234,56.");
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(normalized);
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("O valor é grande demais.", exception);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Informe um valor válido.", exception);
        }
    }

    private static String formatDecimal(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).setScale(2).toPlainString().replace('.', ',');
    }
}
