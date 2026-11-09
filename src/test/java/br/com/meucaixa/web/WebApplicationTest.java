package br.com.meucaixa.web;

import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.MeuCaixaWebApplication;
import br.com.meucaixa.persistence.jdbc.LedgerRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:webtest;DB_CLOSE_DELAY=-1",
    "spring.sql.init.mode=always"
})
public class WebApplicationTest {
    @Autowired
    private TestRestTemplate http;

    @Autowired
    private LedgerRepository repository;

    @Test
    public void homePageRendersFinanceDashboard() {
        ResponseEntity<String> response = http.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Meu Caixa"));
        assertTrue(response.getBody().contains("Movimentos do mês"));
        assertTrue(response.getBody().contains("Sobra do mês"));
    }

    @Test
    public void repositoryPersistsTransactionsAndOpeningBalance() {
        Transaction transaction = new Transaction(repository.nextId(), Transaction.Type.INCOME,
                12550, "Salário", "Teste web", java.time.LocalDate.of(2026, 11, 6));
        repository.insert(transaction);
        repository.setOpeningBalance(6000);
        assertEquals("Teste web", repository.findAll().get(0).getDescription());
        assertEquals(6000L, repository.getOpeningBalance());
        assertTrue(repository.delete(transaction.getId()));
    }

    @Test
    public void clearTransactionsRemovesOnlySelectedMonth() {
        Transaction october = new Transaction(repository.nextId(), Transaction.Type.EXPENSE,
                1000, "Casa", "Outubro teste", java.time.LocalDate.of(2099, 10, 5));
        repository.insert(october);
        Transaction november = new Transaction(repository.nextId(), Transaction.Type.INCOME,
                2000, "Salário", "Novembro teste", java.time.LocalDate.of(2099, 11, 5));
        repository.insert(november);

        ResponseEntity<String> response = http.postForEntity("/transactions/clear?month=2099-10", null, String.class);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertTrue(repository.findAllByIdAscending().stream().noneMatch(row -> row.getDate().getYear() == 2099 && row.getDate().getMonthValue() == 10));
        assertTrue(repository.findAllByIdAscending().stream().anyMatch(row -> row.getId() == november.getId()));
        assertTrue(repository.delete(november.getId()));
    }
}
