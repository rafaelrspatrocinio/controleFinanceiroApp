package br.com.meucaixa.bootstrap;

import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.persistence.file.LedgerStore;
import br.com.meucaixa.persistence.jdbc.LedgerRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("web")
public class InitialDataLoader implements ApplicationRunner {
    private final LedgerRepository repository;

    public InitialDataLoader(LedgerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (repository.isInitialized()) return;
        String dataDirectory = System.getenv("DATA_DIR");
        Path localDirectory = dataDirectory == null || dataDirectory.trim().isEmpty()
                ? Paths.get(System.getProperty("user.home"), ".meu-caixa-java")
                : Paths.get(dataDirectory);
        LedgerStore store = new LedgerStore(localDirectory);
        List<Transaction> initial = store.loadTransactions();
        long openingBalance = store.loadOpeningBalance();
        repository.initialize(initial, openingBalance);
        repository.markInitialized();
        Path existingFile = localDirectory.resolve("transactions.csv");
        if (Files.exists(existingFile)) {
            System.out.println("Dados locais migrados para o banco H2 do Meu Caixa Web.");
        } else {
            System.out.println("Banco H2 do Meu Caixa Web inicializado sem lançamentos.");
        }
    }
}
