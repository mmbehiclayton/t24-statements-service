package com.sensys.statement.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sensys.statement.StatementProperties;
import com.sensys.statement.render.CsvRenderer;
import com.sensys.statement.render.PdfRenderer;
import com.sensys.statement.statement.BatchService;
import com.sensys.statement.statement.CustomerStatement;
import com.sensys.statement.statement.CustomerStatementService;
import com.sensys.statement.statement.Statement;
import com.sensys.statement.statement.StatementService;
import com.sensys.statement.statement.StatementService.NotFoundException;
import com.sensys.statement.t24.T24Reader;

@RestController
@RequestMapping("/api")
public class StatementController {

    private static final Logger log = LoggerFactory.getLogger(StatementController.class);

    private final StatementService statements;
    private final BatchService batch;
    private final PdfRenderer pdf;
    private final CsvRenderer csv;
    private final T24Reader reader;
    private final StatementProperties props;
    private final CustomerStatementService customers;

    public StatementController(StatementService statements, BatchService batch, PdfRenderer pdf, CsvRenderer csv,
                               T24Reader reader, StatementProperties props, CustomerStatementService customers) {
        this.customers = customers;
        this.statements = statements;
        this.batch = batch;
        this.pdf = pdf;
        this.csv = csv;
        this.reader = reader;
        this.props = props;
    }

    @GetMapping("/companies")
    public Map<String, Object> companies() {
        return Map.of("companies", reader.companies(), "default", props.defaultCompany());
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> accounts(@RequestParam(required = false) String company,
                                              @RequestParam(defaultValue = "20") int limit) {
        return statements.sampleAccounts(co(company), limit);
    }

    @GetMapping("/statement")
    public ResponseEntity<?> statement(@RequestParam(required = false) String company,
                                       @RequestParam String account,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "json") String format,
                                       @RequestParam(defaultValue = "true") boolean verify) {
        Statement s = statements.build(co(company), account.trim(), from, to, verify);
        String base = account.trim().replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + from + "_" + to;
        long t0 = System.nanoTime();
        switch (format) {
            case "pdf" -> {
                byte[] body = pdf.render(s);
                return file(body, base + ".pdf", MediaType.APPLICATION_PDF, s.timing().withRender(ms(t0)));
            }
            case "csv" -> {
                byte[] body = csv.render(s);
                return file(body, base + ".csv", new MediaType("text", "csv"), s.timing().withRender(ms(t0)));
            }
            default -> {
                pdf.render(s); // time the PDF so the screen shows the full cost
                return ResponseEntity.ok(s.withTiming(s.timing().withRender(ms(t0))));
            }
        }
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@RequestParam(required = false) String company,
                                               @RequestParam(defaultValue = "20") int limit) {
        return customers.sampleCustomers(co(company), limit);
    }

    /** All of a customer's accounts in one statement: summary + per-currency totals, then each account. */
    @GetMapping("/customer-statement")
    public ResponseEntity<?> customerStatement(@RequestParam(required = false) String company,
                                               @RequestParam String customer,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "json") String format) {
        long t0 = System.nanoTime();
        CustomerStatement cs = customers.build(co(company), customer.trim(), from, to);
        String base = "customer_" + customer.trim().replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + from + "_" + to;
        switch (format) {
            case "pdf" -> {
                byte[] body = pdf.render(cs);
                return customerFile(body, base + ".pdf", MediaType.APPLICATION_PDF, ms(t0));
            }
            case "csv" -> {
                byte[] body = csv.render(cs);
                return customerFile(body, base + ".csv", new MediaType("text", "csv"), ms(t0));
            }
            default -> {
                pdf.render(cs); // time the PDF so the screen shows the full cost
                return ResponseEntity.ok().header("X-Statement-Ms", String.valueOf(ms(t0)))
                        .body(Map.of("statement", cs, "totalMs", ms(t0), "lines", cs.lineCount()));
            }
        }
    }

    private static ResponseEntity<byte[]> customerFile(byte[] body, String name, MediaType type, long totalMs) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header("X-Statement-Ms", String.valueOf(totalMs))
                .body(body);
    }

    public record BatchRequest(String company, List<String> accounts, Integer count,
                               LocalDate from, LocalDate to) {
    }

    @PostMapping("/batch")
    public BatchService.Summary batch(@RequestBody BatchRequest req) {
        if (req.from() == null || req.to() == null) throw new IllegalArgumentException("from and to are required");
        return batch.run(co(req.company()), req.accounts(), req.count() == null ? 50 : req.count(), req.from(), req.to());
    }

    private static final java.util.regex.Pattern BATCH_FOLDER = java.util.regex.Pattern.compile("batch-\\d{8}-\\d{6}");
    private static final java.util.regex.Pattern BATCH_FILE = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]+\\.(pdf|csv)");

    /** Files written by one batch run, for the page to link to. */
    @GetMapping("/batch/{folder}")
    public List<Map<String, Object>> batchFiles(@PathVariable String folder) throws java.io.IOException {
        Path dir = batchDir(folder);
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .sorted()
                    .map(p -> Map.<String, Object>of("name", p.getFileName().toString(), "bytes", p.toFile().length()))
                    .toList();
        }
    }

    @GetMapping("/batch/{folder}/{file:.+}")
    public ResponseEntity<byte[]> batchFile(@PathVariable String folder, @PathVariable String file) throws java.io.IOException {
        if (!BATCH_FILE.matcher(file).matches()) throw new IllegalArgumentException("Invalid file name");
        Path p = batchDir(folder).resolve(file);
        if (!Files.isRegularFile(p)) throw new NotFoundException("File not found");
        MediaType type = file.endsWith(".pdf") ? MediaType.APPLICATION_PDF : new MediaType("text", "csv");
        boolean inline = file.endsWith(".pdf");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(file).build().toString())
                .body(Files.readAllBytes(p));
    }

    private Path batchDir(String folder) {
        if (!BATCH_FOLDER.matcher(folder).matches()) throw new IllegalArgumentException("Invalid batch folder");
        Path dir = Path.of(props.outputDir()).resolve(folder);
        if (!Files.isDirectory(dir)) throw new NotFoundException("Batch not found");
        return dir;
    }

    private String co(String company) {
        return company == null || company.isBlank() ? props.defaultCompany() : company.trim().toUpperCase();
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, MediaType type, Statement.Timing t) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header("X-Statement-Ms", String.valueOf(t.totalMs()))
                .body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        log.error("Request failed", e);
        return ResponseEntity.internalServerError().body(Map.of("error", "Statement could not be produced: " + e.getClass().getSimpleName()));
    }
}
