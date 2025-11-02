package com.example.candidate_registry.service;

import com.example.candidate_registry.dto.CsvUploadResult;
import com.example.candidate_registry.entity.Candidate;
import com.example.candidate_registry.repository.CandidateRepository;
import org.apache.commons.csv.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CandidateService {

    private final CandidateRepository repo;

    @Value("${app.upload.max-size-bytes:5242880}") // 5MB
    private long maxFileSize;

    @Value("${app.upload.max-rows:10000}")
    private int maxRows;

    @Value("${app.error.report.dir:/tmp}")
    private String errorReportDir;

    public CandidateService(CandidateRepository repo) {
        this.repo = repo;
    }

    public Page<Candidate> search(String name, String nationality, String origin, Pageable pageable) {
        if (name == null)
            name = "";
        if (nationality == null)
            nationality = "";
        if (origin == null)
            origin = "";
        return repo.findByNameContainingIgnoreCaseAndNationalityContainingIgnoreCaseAndOriginContainingIgnoreCase(
                name, nationality, origin, pageable);
    }

    @Transactional
    public CsvUploadResult uploadCsv(MultipartFile file, String baseUrl) throws IOException {
        if (file == null)
            throw new IllegalArgumentException("file is required");
        if (file.getSize() > maxFileSize) {
            CsvUploadResult err = new CsvUploadResult();
            throw new FileSizeLimitExceededException("FILE_LIMIT", "File too large (max 5MB).");
        }

        // read bytes into reader (ensure UTF-8)
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

        CSVFormat format = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withTrim(false)
                .withIgnoreEmptyLines(false)
                .withIgnoreSurroundingSpaces(false)
                .withAllowMissingColumnNames(false);

        CSVParser parser;
        try {
            parser = format.parse(reader);
        } catch (IOException ex) {
            throw new RuntimeException("MALFORMED_CSV: " + ex.getMessage(), ex);
        }

        Map<String, Integer> headerMap = parser.getHeaderMap();
        if (headerMap == null || headerMap.isEmpty()) {
            throw new RuntimeException("MALFORMED_CSV: empty header");
        }

        // header duplicates detection (commons-csv merges duplicates but we must detect
        // duplicates by raw header list)
        List<String> rawHeaders = new ArrayList<>();
        try (BufferedReader rr = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String first = rr.readLine();
            if (first == null)
                throw new RuntimeException("MALFORMED_CSV: empty file");
            String[] parts = first.split(",", -1);
            Set<String> seen = new HashSet<>();
            for (String h : parts) {
                if (h == null || h.trim().isEmpty())
                    throw new RuntimeException("MALFORMED_CSV: empty header");
                if (!seen.add(h))
                    throw new RuntimeException("MALFORMED_CSV: duplicate header '" + h + "'");
            }
        }

        // allowed headers
        Set<String> allowed = Set.of("external_ref", "name", "age", "nationality", "origin", "notes");
        List<Map<String, String>> warnings = new ArrayList<>();
        for (String h : headerMap.keySet()) {
            if (!allowed.contains(h)) {
                Map<String, String> w = new HashMap<>();
                w.put("type", "UNKNOWN_HEADER");
                w.put("message", "Header '" + h + "' is ignored.");
                warnings.add(w);
            }
        }

        List<CSVRecord> records = parser.getRecords();
        if (records.size() > maxRows) {
            throw new RuntimeException("FILE_LIMIT: too many rows");
        }

        CsvUploadResult result = new CsvUploadResult();
        result.totalRows = records.size();
        result.successCount = 0;
        result.failureCount = 0;
        result.warnings = warnings;
        result.errorReport = new CsvUploadResult.ErrorReport();
        List<String[]> errorRows = new ArrayList<>();

        // detect DUP_IN_FILE
        Map<String, List<Integer>> extRefLines = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            CSVRecord r = records.get(i);
            String raw = r.get("external_ref") == null ? "" : r.get("external_ref");
            String norm = normalize(raw);
            if (norm != null && !norm.isEmpty()) {
                extRefLines.computeIfAbsent(norm, k -> new ArrayList<>()).add(i + 2); // +2: header + 1-based
            }
        }
        Set<String> dupInFile = new HashSet<>();
        for (Map.Entry<String, List<Integer>> e : extRefLines.entrySet()) {
            if (e.getValue().size() > 1)
                dupInFile.add(e.getKey());
        }

        // store which columns present in CSV
        Set<String> presentCols = new HashSet<>(headerMap.keySet());

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String ts = LocalDateTime.now().format(dtf);
        String errFileName = "upload-errors-" + ts + ".csv";
        File errFile = new File(errorReportDir, errFileName);
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(errFile), StandardCharsets.UTF_8));
                CSVPrinter errPrinter = CSVFormat.DEFAULT.withHeader("row_number", "error_code", "error_message",
                        "external_ref", "name", "age", "nationality", "origin", "notes").print(bw)) {

            // process rows
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                int rowNumber = (int) record.getRecordNumber() + 0; // parser already uses 1-based after header
                String externalRefRaw = record.isMapped("external_ref") ? record.get("external_ref") : "";
                String externalRef = normalize(externalRefRaw);
                String name = record.isMapped("name") ? normalize(record.get("name")) : null;
                String ageRaw = record.isMapped("age") ? record.get("age") : null;
                String nationality = record.isMapped("nationality") ? normalize(record.get("nationality")) : null;
                String origin = record.isMapped("origin") ? normalize(record.get("origin")) : null;
                String notes = record.isMapped("notes") ? normalize(record.get("notes")) : null;

                // DUP_IN_FILE check
                if (externalRef != null && dupInFile.contains(externalRef)) {
                    result.failureCount++;
                    errPrinter.printRecord(rowNumber, "DUP_IN_FILE", "duplicate external_ref in file", externalRef,
                            name, ageRaw, nationality, origin, notes);
                    continue;
                }

                // validation
                List<String> errs = new ArrayList<>();
                if (externalRef == null || externalRef.isEmpty())
                    errs.add("REQ_MISSING: external_ref is required");
                if (name == null || name.isEmpty())
                    errs.add("REQ_MISSING: name is required");
                if (externalRef != null && externalRef.length() > 64)
                    errs.add("LEN_OVER: external_ref > 64");
                if (name != null && name.length() > 100)
                    errs.add("LEN_OVER: name > 100");
                if (nationality != null && nationality.length() > 50)
                    errs.add("LEN_OVER: nationality > 50");
                if (origin != null && origin.length() > 100)
                    errs.add("LEN_OVER: origin > 100");
                if (notes != null && notes.length() > 2000)
                    errs.add("LEN_OVER: notes > 2000");

                Integer age = null;
                if (ageRaw != null) {
                    String t = ageRaw.trim();
                    if (t.isEmpty()) {
                        age = null;
                    } else {
                        try {
                            double maybe = Double.parseDouble(t);
                            if (t.contains(".") || t.contains(",")) {
                                errs.add("TYPE_MISMATCH: age must be integer");
                            } else {
                                age = Integer.parseInt(t);
                                if (age < 0 || age > 200)
                                    errs.add("RANGE_ERROR: age must be 0..200");
                            }
                        } catch (NumberFormatException ex) {
                            errs.add("TYPE_MISMATCH: age must be integer");
                        }
                    }
                }

                if (!errs.isEmpty()) {
                    result.failureCount++;
                    String errMsg = String.join("; ", errs);
                    errPrinter.printRecord(rowNumber, errs.get(0).split(":")[0], errMsg, externalRef, name, ageRaw,
                            nationality, origin, notes);
                    continue;
                }

                // upsert: external_ref -> update only columns present in CSV
                try {
                    Optional<Candidate> opt = externalRef == null ? Optional.empty()
                            : repo.findByExternalRef(externalRef);
                    Candidate c;
                    if (opt.isPresent()) {
                        c = opt.get();
                        // only override specified columns
                        if (presentCols.contains("name"))
                            c.setName(name == null ? c.getName() : name);
                        if (presentCols.contains("age"))
                            c.setAge(age);
                        if (presentCols.contains("nationality"))
                            c.setNationality(nationality);
                        if (presentCols.contains("origin"))
                            c.setOrigin(origin);
                        if (presentCols.contains("notes"))
                            c.setNotes(notes);
                    } else {
                        c = new Candidate();
                        c.setExternalRef(externalRef);
                        c.setName(name);
                        c.setAge(age);
                        c.setNationality(nationality);
                        c.setOrigin(origin);
                        c.setNotes(notes);
                    }
                    repo.save(c);
                    result.successCount++;
                } catch (Exception ex) {
                    result.failureCount++;
                    errPrinter.printRecord(rowNumber, "UNKNOWN_ERROR", ex.getMessage(), externalRef, name, ageRaw,
                            nationality, origin, notes);
                }
            }

            errPrinter.flush();
        }

        // if there are any failures, provide download URL
        if (result.failureCount > 0) {
            result.errorReport.available = true;
            String encoded = URLEncoder.encode(errFileName, StandardCharsets.UTF_8);
            String downloadUrl = (baseUrl != null ? baseUrl : "") + "/api/candidates/csv/upload/errors/" + encoded;
            result.errorReport.downloadUrl = downloadUrl;
        } else {
            result.errorReport.available = false;
            result.errorReport.downloadUrl = null;
            // delete empty error file
            if (errFile.exists() && errFile.length() == 0)
                errFile.delete();
        }

        return result;
    }

    private String normalize(String s) {
        if (s == null)
            return null;
        // trim both half-width and full-width spaces, and trim newlines
        String t = s.replace("\u3000", " ").trim();
        if (t.isEmpty())
            return null;
        return t;
    }

    public void writeCsvAll(List<Candidate> candidates, OutputStream os) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                CSVPrinter printer = CSVFormat.RFC4180
                        .withHeader("external_ref", "name", "age", "nationality", "origin", "notes").print(bw)) {
            for (Candidate c : candidates) {
                printer.printRecord(
                        c.getExternalRef(),
                        c.getName(),
                        c.getAge() == null ? "" : c.getAge().toString(),
                        c.getNationality(),
                        c.getOrigin(),
                        c.getNotes());
            }
            printer.flush();
        }
    }

    public File getErrorReportFile(String filename) {
        File f = new File(errorReportDir, filename);
        if (f.exists())
            return f;
        return null;
    }

    // custom exception for file size
    public static class FileSizeLimitExceededException extends RuntimeException {
        public final String code;

        public FileSizeLimitExceededException(String code, String msg) {
            super(msg);
            this.code = code;
        }
    }
}
