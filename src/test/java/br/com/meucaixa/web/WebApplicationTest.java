package br.com.meucaixa.web;

import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.MeuCaixaWebApplication;
import br.com.meucaixa.persistence.file.LedgerStore;
import br.com.meucaixa.persistence.jdbc.LedgerRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
        Transaction expense = new Transaction(repository.nextId(), Transaction.Type.EXPENSE,
                1000, "Casa", "Despesa para marcar", java.time.LocalDate.now());
        repository.insert(expense);
        ResponseEntity<String> response = http.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Meu Caixa"));
        assertTrue(response.getBody().contains("Movimentos do mês"));
        assertTrue(response.getBody().contains("Sobra do mês"));
        assertTrue(response.getBody().contains("edit-dialog"));
        assertTrue(response.getBody().contains("Editar"));
        assertTrue(response.getBody().contains("paid-toggle"));
        assertTrue(response.getBody().contains("Pendente"));
        assertTrue(repository.delete(expense.getId()));
    }

    @Test
    public void repositoryPersistsTransactionsAndOpeningBalance() {
        Transaction transaction = new Transaction(repository.nextId(), Transaction.Type.INCOME,
                12550, "Salário", "Teste web", java.time.LocalDate.of(2026, 11, 6));
        repository.insert(transaction);
        repository.setOpeningBalance(6000);
        assertEquals("Teste web", repository.findById(transaction.getId()).getDescription());
        assertEquals(6000L, repository.getOpeningBalance());
        assertTrue(repository.delete(transaction.getId()));
    }

    @Test
    public void editTransactionUpdatesAllFields() {
        Transaction transaction = new Transaction(repository.nextId(), Transaction.Type.EXPENSE,
                1000, "Mercado", "Antes", java.time.LocalDate.of(2099, 12, 5));
        repository.insert(transaction);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("type", "INCOME");
        form.add("amount", "25,50");
        form.add("category", "Renda Fixa");
        form.add("description", "Depois");
        form.add("date", "2099-12-10");
        form.add("month", "2099-12");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = http.postForEntity("/transactions/" + transaction.getId() + "/edit",
                new HttpEntity<MultiValueMap<String, String>>(form, headers), String.class);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        Transaction updated = repository.findById(transaction.getId());
        assertEquals(Transaction.Type.INCOME, updated.getType());
        assertEquals(2550L, updated.getAmountCents());
        assertEquals("Renda Fixa", updated.getCategory());
        assertEquals("Depois", updated.getDescription());
        assertEquals(java.time.LocalDate.of(2099, 12, 10), updated.getDate());
        assertTrue(repository.delete(transaction.getId()));
    }

    @Test
    public void expensePaymentCanBeMarkedAndUnmarked() {
        Transaction expense = new Transaction(repository.nextId(), Transaction.Type.EXPENSE,
                1000, "Casa", "Conta de teste", java.time.LocalDate.of(2099, 11, 5));
        repository.insert(expense);

        ResponseEntity<String> paidResponse = http.postForEntity("/transactions/" + expense.getId()
                + "/paid?month=2099-11&paid=true", null, String.class);
        assertEquals(HttpStatus.FOUND, paidResponse.getStatusCode());
        assertTrue(repository.findById(expense.getId()).isPaid());
        assertTrue(http.getForObject("/?month=2099-11", String.class).contains("Pago"));

        ResponseEntity<String> unpaidResponse = http.postForEntity("/transactions/" + expense.getId()
                + "/paid?month=2099-11", null, String.class);
        assertEquals(HttpStatus.FOUND, unpaidResponse.getStatusCode());
        assertTrue(!repository.findById(expense.getId()).isPaid());
        assertTrue(repository.delete(expense.getId()));
    }

    @Test
    public void backupsPreservePaidExpensesAndAcceptLegacyFiles() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("paid-backup-", ".csv");
        try {
            Transaction paid = new Transaction(1, Transaction.Type.EXPENSE, 2500, "Casa", "Conta",
                    java.time.LocalDate.of(2099, 11, 5), true);
            LedgerStore.writeBackup(file, java.util.Collections.singletonList(paid));
            assertTrue(LedgerStore.readBackup(file).get(0).isPaid());

            java.nio.file.Files.write(file, ("id,type,amount_cents,category,description,date\n"
                    + "1,EXPENSE,2500,Casa,Conta,2099-11-05\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(!LedgerStore.readBackup(file).get(0).isPaid());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
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
