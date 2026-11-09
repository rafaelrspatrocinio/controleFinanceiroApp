package br.com.meucaixa.persistence.jdbc;

import br.com.meucaixa.domain.model.Transaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<Transaction> transactionMapper = new RowMapper<Transaction>() {
        @Override
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Transaction(rs.getLong("id"), Transaction.Type.valueOf(rs.getString("type")),
                    rs.getLong("amount_cents"), rs.getString("category"), rs.getString("description"),
                    rs.getDate("date").toLocalDate());
        }
    };

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Transaction> findAll() {
        return jdbc.query("SELECT id, type, amount_cents, category, description, date FROM transactions ORDER BY date DESC, id DESC", transactionMapper);
    }

    public List<Transaction> findAllByIdAscending() {
        return jdbc.query("SELECT id, type, amount_cents, category, description, date FROM transactions ORDER BY id", transactionMapper);
    }

    public boolean isEmpty() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class) == 0;
    }

    public boolean isInitialized() {
        return jdbc.queryForObject("SELECT initialized FROM initialization_state WHERE id = 1", Boolean.class);
    }

    @Transactional
    public void markInitialized() {
        jdbc.update("UPDATE initialization_state SET initialized = TRUE WHERE id = 1");
    }

    @Transactional
    public void replaceAll(List<Transaction> transactions) {
        jdbc.update("DELETE FROM transactions");
        for (Transaction transaction : transactions) insert(transaction);
    }

    @Transactional
    public void insert(Transaction transaction) {
        jdbc.update("INSERT INTO transactions (id, type, amount_cents, category, description, date) VALUES (?, ?, ?, ?, ?, ?)",
                transaction.getId(), transaction.getType().name(), transaction.getAmountCents(),
                transaction.getCategory(), transaction.getDescription(), java.sql.Date.valueOf(transaction.getDate()));
    }

    @Transactional
    public boolean delete(long id) {
        return jdbc.update("DELETE FROM transactions WHERE id = ?", id) > 0;
    }

    @Transactional
    public int deleteByMonth(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        return jdbc.update("DELETE FROM transactions WHERE date >= ? AND date < ?",
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    public long nextId() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM transactions", Long.class);
    }

    public long getOpeningBalance() {
        return jdbc.queryForObject("SELECT opening_balance_cents FROM ledger_settings WHERE id = 1", Long.class);
    }

    @Transactional
    public void setOpeningBalance(long cents) {
        if (cents < 0) throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
        jdbc.update("UPDATE ledger_settings SET opening_balance_cents = ? WHERE id = 1", cents);
    }

    @Transactional
    public void initialize(List<Transaction> initialTransactions, long openingBalanceCents) {
        if (isEmpty() && !initialTransactions.isEmpty()) replaceAll(initialTransactions);
        jdbc.update("UPDATE ledger_settings SET opening_balance_cents = ? WHERE id = 1 AND opening_balance_cents = 0", openingBalanceCents);
    }
}
