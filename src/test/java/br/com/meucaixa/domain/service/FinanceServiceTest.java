package br.com.meucaixa.domain.service;

import br.com.meucaixa.desktop.ui.MainFrame;
import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.persistence.file.LedgerStore;
import java.time.LocalDate;
import java.time.YearMonth;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FinanceServiceTest {
    public static void main(String[] args) throws Exception {
        List<Transaction> rows = new ArrayList<Transaction>();
        rows.add(new Transaction(1, Transaction.Type.INCOME, 250000, "Salário", "Pagamento, com \"detalhe\"", LocalDate.parse("2026-08-01")));
        rows.add(new Transaction(2, Transaction.Type.EXPENSE, 70000, "Casa", "Aluguel", LocalDate.parse("2026-08-04")));
        rows.add(new Transaction(3, Transaction.Type.INCOME, 50000, "Extra", "Venda", LocalDate.parse("2026-09-02")));
        YearMonth august = YearMonth.of(2026, 8);
        check(FinanceService.incomeForMonth(rows, august) == 250000, "Soma das receitas do mês");
        check(FinanceService.expensesForMonth(rows, august) == 70000, "Soma das despesas do mês");
        check(FinanceService.monthlySurplus(rows, august) == 180000, "Sobra mensal é a diferença entre receitas e despesas");
        check(FinanceService.monthlySurplus(rows, YearMonth.of(2026, 10)) == 0, "Mês sem movimentos não tem sobra");
        check(FinanceService.balanceThroughMonth(rows, august, 10000) == 190000, "Saldo acumulado até o mês");
        check(FinanceService.balanceThroughDate(rows, LocalDate.of(2026, 8, 3), 10000) == 260000, "Saldo considera somente lançamentos até a data");
        check(FinanceService.balanceThroughDate(rows, LocalDate.of(2026, 8, 4), 10000) == 190000, "Saldo inclui lançamentos na data de referência");
        check(FinanceService.balanceDateForMonth(august, LocalDate.of(2026, 11, 9)).equals(LocalDate.of(2026, 8, 31)), "Mês passado usa o último dia do mês");
        check(FinanceService.balanceDateForMonth(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 9)).equals(LocalDate.of(2026, 11, 9)), "Mês atual usa a data de hoje");
        check(FinanceService.balanceDateForMonth(YearMonth.of(2026, 12), LocalDate.of(2026, 11, 9)).equals(LocalDate.of(2026, 11, 9)), "Mês futuro não inclui lançamentos futuros");
        check(FinanceService.balanceThroughMonth(rows, YearMonth.of(2026, 9), 10000) == 240000, "Saldo acumulado inclui mês seguinte");
        check(FinanceService.forMonth(rows, august).size() == 2, "Filtro por mês");
        check(FinanceService.nextId(rows) == 4, "Próximo identificador");
        check(MainFrame.parseMoney("1.234,56") == 123456, "Leitura de valor brasileiro");
        check(MainFrame.parseMoney("R$ 25,00") == 2500, "Leitura de moeda");
        boolean rejected = false;
        try {
            MainFrame.parseMoney("12,345");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Rejeita precisão monetária inválida");
        check(backupRoundTrip(rows), "Exportação e importação CSV com aspas e vírgulas");
        checkSeedAndLocalPersistence();
        System.out.println("FinanceService tests passed.");
    }

    private static boolean backupRoundTrip(List<Transaction> transactions) throws Exception {
        Path file = Files.createTempFile("meu-caixa-test-", ".csv");
        try {
            LedgerStore.writeBackup(file, transactions);
            List<Transaction> restored = LedgerStore.readBackup(file);
            if (restored.size() != transactions.size()) return false;
            for (int index = 0; index < restored.size(); index++) {
                if (!restored.get(index).getDescription().equals(transactions.get(index).getDescription())) return false;
            }
            return true;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void checkSeedAndLocalPersistence() throws Exception {
        Path directory = Files.createTempDirectory("meu-caixa-store-test-");
        try {
            LedgerStore store = new LedgerStore(directory);
            List<Transaction> seed = store.loadTransactions();
            check(seed.isEmpty(), "Instalação sem dados pessoais de exemplo");
            List<Transaction> sample = new ArrayList<Transaction>();
            sample.add(new Transaction(1, Transaction.Type.INCOME, 250000, "Salário", "Pagamento de teste", LocalDate.of(2026, 8, 1)));
            sample.add(new Transaction(2, Transaction.Type.EXPENSE, 70000, "Casa", "Aluguel de teste", LocalDate.of(2026, 8, 4)));
            store.saveOpeningBalance(765432);
            store.saveTransactions(sample);
            LedgerStore reopened = new LedgerStore(directory);
            check(reopened.loadTransactions().size() == 2, "Persistência dos movimentos locais");
            check(reopened.loadOpeningBalance() == 765432, "Persistência do saldo inicial");
            reopened.saveTransactions(new ArrayList<Transaction>());
            check(new LedgerStore(directory).loadTransactions().isEmpty(), "Limpeza persistente de todos os movimentos");
            check(new LedgerStore(directory).loadOpeningBalance() == 765432, "A limpeza mantém o saldo inicial");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
                });
            }
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
