package br.com.meucaixa.web.controller;

import br.com.meucaixa.domain.model.Transaction;
import br.com.meucaixa.domain.service.FinanceService;
import br.com.meucaixa.persistence.file.LedgerStore;
import br.com.meucaixa.persistence.jdbc.LedgerRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {
    private static final String[] CATEGORIES = {
        "Renda Fixa", "Renda Variável", "Custos Fixos", "Despesa", "Mercado", "Cartão",
        "Dívida", "Pessoal", "Educação", "Saúde", "Pet", "Lazer", "Manutenção", "Outros"
    };
    private final LedgerRepository repository;

    public WebController(LedgerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String index(@RequestParam(value = "month", required = false) String monthValue,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error, Model model) {
        YearMonth month = parseMonth(monthValue);
        LocalDate today = LocalDate.now();
        LocalDate balanceDate = month.isBefore(YearMonth.from(today)) ? month.atEndOfMonth() : today;
        List<Transaction> transactions = repository.findAllByIdAscending();
        model.addAttribute("month", month);
        model.addAttribute("monthLabel", month.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"))));
        model.addAttribute("transactions", FinanceService.forMonth(transactions, month));
        model.addAttribute("income", money(FinanceService.incomeForMonth(transactions, month)));
        model.addAttribute("expenses", money(FinanceService.expensesForMonth(transactions, month)));
        model.addAttribute("monthlySurplus", money(FinanceService.monthlySurplus(transactions, month)));
        model.addAttribute("balance", money(FinanceService.balanceThroughDate(transactions, balanceDate, repository.getOpeningBalance())));
        model.addAttribute("balanceDate", balanceDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("openingBalance", decimal(repository.getOpeningBalance()));
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "index";
    }

    @PostMapping("/transactions")
    public String addTransaction(@RequestParam String type, @RequestParam String amount,
            @RequestParam String category, @RequestParam String description, @RequestParam String date,
            RedirectAttributes redirect) {
        try {
            Transaction transaction = new Transaction(repository.nextId(), Transaction.Type.fromLabel(type),
                    parseMoney(amount), category, description, LocalDate.parse(date));
            repository.insert(transaction);
            redirect.addAttribute("month", YearMonth.from(transaction.getDate()));
            redirect.addFlashAttribute("message", "Lançamento salvo.");
        } catch (RuntimeException exception) {
            redirect.addAttribute("error", safeMessage(exception, "Confira os dados do lançamento."));
            redirect.addAttribute("month", safeMonth(date));
        }
        return "redirect:/";
    }

    @PostMapping("/transactions/{id}/delete")
    public String deleteTransaction(@PathVariable long id, @RequestParam String month, RedirectAttributes redirect) {
        if (repository.delete(id)) redirect.addFlashAttribute("message", "Lançamento excluído.");
        else redirect.addFlashAttribute("error", "O lançamento não foi encontrado.");
        redirect.addAttribute("month", parseMonth(month));
        return "redirect:/";
    }

    @PostMapping("/transactions/clear")
    public String clearTransactions(@RequestParam String month, RedirectAttributes redirect) {
        YearMonth selectedMonth = parseMonth(month);
        repository.deleteByMonth(selectedMonth);
        redirect.addFlashAttribute("message", "Lançamentos do mês removidos; o saldo inicial foi mantido.");
        redirect.addAttribute("month", selectedMonth);
        return "redirect:/";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam String openingBalance, @RequestParam String month, RedirectAttributes redirect) {
        try {
            repository.setOpeningBalance(parseMoney(openingBalance));
            redirect.addFlashAttribute("message", "Saldo inicial atualizado.");
        } catch (RuntimeException exception) {
            redirect.addFlashAttribute("error", safeMessage(exception, "Informe um saldo inicial válido."));
        }
        redirect.addAttribute("month", parseMonth(month));
        return "redirect:/";
    }

    @GetMapping("/backup.csv")
    public void exportBackup(HttpServletResponse response) throws IOException {
        Path file = Files.createTempFile("meu-caixa-backup-", ".csv");
        try {
            LedgerStore.writeBackup(file, repository.findAllByIdAscending());
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"meu-caixa-backup.csv\"");
            Files.copy(file, response.getOutputStream());
            response.flushBuffer();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @PostMapping("/backup.csv")
    public String importBackup(@RequestParam("file") MultipartFile upload,
            @RequestParam String month, RedirectAttributes redirect) {
        if (upload.isEmpty()) {
            redirect.addFlashAttribute("error", "Selecione um arquivo CSV.");
        } else {
            Path file = null;
            try {
                file = Files.createTempFile("meu-caixa-upload-", ".csv");
                Files.copy(upload.getInputStream(), file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                List<Transaction> imported = LedgerStore.readBackup(file);
                repository.replaceAll(imported);
                redirect.addFlashAttribute("message", "Cópia importada: " + imported.size() + " lançamentos.");
            } catch (IOException | RuntimeException exception) {
                redirect.addFlashAttribute("error", safeMessage(exception, "Não foi possível importar a cópia CSV."));
            } finally {
                if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
            }
        }
        redirect.addAttribute("month", parseMonth(month));
        return "redirect:/";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidRequest(IllegalArgumentException exception, Model model) {
        model.addAttribute("error", safeMessage(exception, "Requisição inválida."));
        return "error";
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.trim().isEmpty()) return YearMonth.now();
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException exception) { return YearMonth.now(); }
    }

    private static YearMonth safeMonth(String date) {
        try { return YearMonth.from(LocalDate.parse(date)); }
        catch (RuntimeException exception) { return YearMonth.now(); }
    }

    private static long parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Informe um valor.");
        String normalized = value.trim().replace("R$", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(',', '.');
        if (!normalized.matches("-?\\d+(\\.\\d{1,2})?")) throw new IllegalArgumentException("Use um valor como 1234,56.");
        try { return new BigDecimal(normalized).movePointRight(2).longValueExact(); }
        catch (ArithmeticException | NumberFormatException exception) { throw new IllegalArgumentException("Informe um valor válido.", exception); }
    }

    private static String money(long cents) {
        return java.text.NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(cents / 100.0);
    }

    private static String decimal(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',');
    }

    private static String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
