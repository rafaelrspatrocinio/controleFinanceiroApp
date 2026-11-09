package br.com.meucaixa.domain.service;

import br.com.meucaixa.domain.model.Transaction;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class FinanceService {
    private FinanceService() { }

    public static List<Transaction> forMonth(List<Transaction> transactions, YearMonth month) {
        List<Transaction> result = new ArrayList<Transaction>();
        for (Transaction transaction : transactions) {
            if (YearMonth.from(transaction.getDate()).equals(month)) result.add(transaction);
        }
        sortNewestFirst(result);
        return result;
    }

    public static long incomeForMonth(List<Transaction> transactions, YearMonth month) {
        return sum(transactions, month, Transaction.Type.INCOME, false);
    }

    public static long expensesForMonth(List<Transaction> transactions, YearMonth month) {
        return sum(transactions, month, Transaction.Type.EXPENSE, false);
    }

    public static long monthlySurplus(List<Transaction> transactions, YearMonth month) {
        return Math.subtractExact(incomeForMonth(transactions, month), expensesForMonth(transactions, month));
    }

    public static long balanceThroughMonth(List<Transaction> transactions, YearMonth month, long openingBalanceCents) {
        return balanceThroughDate(transactions, month.atEndOfMonth(), openingBalanceCents);
    }

    public static long balanceThroughDate(List<Transaction> transactions, LocalDate date, long openingBalanceCents) {
        long balance = openingBalanceCents;
        for (Transaction transaction : transactions) {
            if (!transaction.getDate().isAfter(date)) balance = Math.addExact(balance, transaction.signedAmountCents());
        }
        return balance;
    }

    public static LocalDate balanceDateForMonth(YearMonth month, LocalDate today) {
        return month.isBefore(YearMonth.from(today)) ? month.atEndOfMonth() : today;
    }

    public static long nextId(List<Transaction> transactions) {
        long max = 0;
        for (Transaction transaction : transactions) max = Math.max(max, transaction.getId());
        return Math.addExact(max, 1);
    }

    public static void sortNewestFirst(List<Transaction> transactions) {
        Collections.sort(transactions, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction left, Transaction right) {
                int byDate = right.getDate().compareTo(left.getDate());
                return byDate != 0 ? byDate : Long.compare(right.getId(), left.getId());
            }
        });
    }

    private static long sum(List<Transaction> transactions, YearMonth month, Transaction.Type type, boolean signed) {
        long total = 0;
        for (Transaction transaction : transactions) {
            if (YearMonth.from(transaction.getDate()).equals(month) && transaction.getType() == type) {
                total = Math.addExact(total, signed ? transaction.signedAmountCents() : transaction.getAmountCents());
            }
        }
        return total;
    }
}
