package br.com.meucaixa.persistence.file;

import br.com.meucaixa.domain.model.Transaction;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class LedgerStore {
    private static final String HEADER = "id,type,amount_cents,category,description,date";
    private final Path directory;
    private final Path transactionsFile;
    private final Path settingsFile;

    public LedgerStore() {
        this(Paths.get(System.getProperty("user.home"), ".meu-caixa-java"));
    }

    public LedgerStore(Path directory) {
        this.directory = directory;
        this.transactionsFile = directory.resolve("transactions.csv");
        this.settingsFile = directory.resolve("settings.properties");
    }

    public synchronized List<Transaction> loadTransactions() throws IOException {
        if (!Files.exists(transactionsFile)) {
            List<Transaction> initial = new ArrayList<Transaction>();
            saveTransactions(initial);
            return initial;
        }
        List<Transaction> result = new ArrayList<Transaction>();
        try (BufferedReader reader = Files.newBufferedReader(transactionsFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null || !line.replace("\uFEFF", "").equals(HEADER)) {
                throw new IOException("O arquivo de lançamentos está inválido. Restaure uma cópia CSV válida.");
            }
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                try {
                    List<String> fields = parseCsvLine(line);
                    if (fields.size() != 6) throw new IllegalArgumentException("Quantidade de colunas inválida.");
                    result.add(new Transaction(Long.parseLong(fields.get(0)), Transaction.Type.fromLabel(fields.get(1)),
                            Long.parseLong(fields.get(2)), fields.get(3), fields.get(4), LocalDate.parse(fields.get(5))));
                } catch (RuntimeException exception) {
                    throw new IOException("Registro inválido na linha " + lineNumber + " do arquivo local.", exception);
                }
            }
        }
        return result;
    }

    public synchronized void saveTransactions(List<Transaction> transactions) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "transactions-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();
                List<Transaction> ordered = new ArrayList<Transaction>(transactions);
                Collections.sort(ordered, Comparator.comparingLong(Transaction::getId));
                for (Transaction transaction : ordered) {
                    writer.write(transaction.getId() + "," + transaction.getType().name() + "," + transaction.getAmountCents()
                            + "," + csv(transaction.getCategory()) + "," + csv(transaction.getDescription()) + "," + transaction.getDate());
                    writer.newLine();
                }
            }
            replaceAtomically(temporary, transactionsFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized long loadOpeningBalance() throws IOException {
        if (!Files.exists(settingsFile)) return 0;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
        }
        try {
            long balance = Long.parseLong(properties.getProperty("openingBalanceCents", "0"));
            if (balance < 0) throw new NumberFormatException("Saldo negativo");
            return balance;
        } catch (NumberFormatException exception) {
            throw new IOException("O saldo inicial salvo está inválido.", exception);
        }
    }

    public synchronized void saveOpeningBalance(long cents) throws IOException {
        if (cents < 0) throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "settings-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("openingBalanceCents", Long.toString(cents));
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
            replaceAtomically(temporary, settingsFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path getTransactionsFile() {
        return transactionsFile;
    }

    public static List<Transaction> readBackup(Path file) throws IOException {
        List<Transaction> result = new ArrayList<Transaction>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.replace("\uFEFF", "").equals(HEADER)) {
                throw new IOException("Selecione um arquivo de cópia CSV do Meu Caixa.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> fields = parseCsvLine(line);
                if (fields.size() != 6) throw new IOException("A cópia contém uma linha inválida.");
                try {
                    result.add(new Transaction(Long.parseLong(fields.get(0)), Transaction.Type.fromLabel(fields.get(1)),
                            Long.parseLong(fields.get(2)), fields.get(3), fields.get(4), LocalDate.parse(fields.get(5))));
                } catch (RuntimeException exception) {
                    throw new IOException("A cópia contém valores inválidos.", exception);
                }
            }
        }
        result.sort(Comparator.comparingLong(Transaction::getId));
        for (int index = 1; index < result.size(); index++) {
            if (result.get(index - 1).getId() == result.get(index).getId()) throw new IOException("A cópia contém identificadores duplicados.");
        }
        return result;
    }

    public static void writeBackup(Path file, List<Transaction> transactions) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Transaction transaction : transactions) {
                writer.write(transaction.getId() + "," + transaction.getType().name() + "," + transaction.getAmountCents()
                        + "," + csv(transaction.getCategory()) + "," + csv(transaction.getDescription()) + "," + transaction.getDate());
                writer.newLine();
            }
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<String> parseCsvLine(String line) throws IOException {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '"' && field.length() == 0) {
                quoted = true;
            } else {
                field.append(character);
            }
        }
        if (quoted) throw new IOException("Campo CSV sem aspas de fechamento.");
        fields.add(field.toString());
        return fields;
    }
}
