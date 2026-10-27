package com.sensys.statement.statement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sensys.statement.StatementProperties;
import com.sensys.statement.render.CsvRenderer;
import com.sensys.statement.render.PdfRenderer;

/** Generates PDF + CSV statements for many accounts in parallel and reports timings. */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final StatementService statements;
    private final PdfRenderer pdf;
    private final CsvRenderer csv;
    private final StatementProperties props;

    public BatchService(StatementService statements, PdfRenderer pdf, CsvRenderer csv, StatementProperties props) {
        this.statements = statements;
        this.pdf = pdf;
        this.csv = csv;
        this.props = props;
    }

    public record Result(String account, boolean ok, int lines, long ms, String error) {
    }

    public record Summary(String company, LocalDate from, LocalDate to, int requested, int ok, int failed,
                          long wallMs, long avgMs, long p95Ms, long maxMs, double statementsPerMinute,
                          long totalLines, int threads, String outputFolder, List<Result> failures) {
    }

    public Summary run(String company, List<String> requestedAccounts, int count, LocalDate from, LocalDate to) {
        List<String> accounts = requestedAccounts == null || requestedAccounts.isEmpty()
                ? statements.sampleAccounts(company, count).stream().map(m -> String.valueOf(m.get("ACCOUNT"))).toList()
                : requestedAccounts;
        if (accounts.size() > props.batchMaxAccounts()) {
            throw new IllegalArgumentException("At most " + props.batchMaxAccounts() + " accounts per batch");
        }
        String folder = "batch-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of(props.outputDir()).resolve(folder);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int threads = Math.max(1, props.batchThreads());
        long start = System.nanoTime();
        List<Result> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Result>> futures = new ArrayList<>();
            for (String account : accounts) {
                futures.add(pool.submit(() -> one(company, account, from, to, dir)));
            }
            for (Future<Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    results.add(new Result("?", false, 0, 0, e.getMessage()));
                }
            }
        }
        long wall = (System.nanoTime() - start) / 1_000_000;

        List<Long> times = new ArrayList<>(results.stream().filter(Result::ok).map(Result::ms).toList());
        Collections.sort(times);
        long avg = times.isEmpty() ? 0 : Math.round(times.stream().mapToLong(Long::longValue).average().orElse(0));
        long p95 = times.isEmpty() ? 0 : times.get(Math.min(times.size() - 1, (int) Math.ceil(times.size() * 0.95) - 1));
        long max = times.isEmpty() ? 0 : times.get(times.size() - 1);
        int ok = times.size();
        double perMin = wall == 0 ? 0 : Math.round(ok * 60_000.0 / wall * 10) / 10.0;
        long lines = results.stream().mapToLong(Result::lines).sum();
        List<Result> failures = results.stream().filter(r -> !r.ok()).limit(20).toList();
        log.info("Batch {}: {} ok, {} failed, {} ms wall", folder, ok, results.size() - ok, wall);
        return new Summary(company, from, to, accounts.size(), ok, results.size() - ok, wall, avg, p95, max,
                perMin, lines, threads, folder, failures);
    }

    private Result one(String company, String account, LocalDate from, LocalDate to, Path dir) {
        long t0 = System.nanoTime();
        try {
            Statement s = statements.build(company, account, from, to, false);
            String base = account.replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + from + "_" + to;
            Files.write(dir.resolve(base + ".pdf"), pdf.render(s));
            Files.write(dir.resolve(base + ".csv"), csv.render(s));
            return new Result(account, true, s.lines().size(), (System.nanoTime() - t0) / 1_000_000, null);
        } catch (Exception e) {
            log.warn("Batch statement failed for {}: {}", account, e.getMessage());
            return new Result(account, false, 0, (System.nanoTime() - t0) / 1_000_000, e.getMessage());
        }
    }

    public Map<String, Object> describe() {
        return Map.of("threads", props.batchThreads(), "maxAccounts", props.batchMaxAccounts());
    }
}
