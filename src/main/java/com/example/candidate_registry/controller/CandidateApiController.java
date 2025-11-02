package com.example.candidate_registry.controller;

import com.example.candidate_registry.dto.CandidateDto;
import com.example.candidate_registry.dto.CsvUploadResult;
import com.example.candidate_registry.entity.Candidate;
import com.example.candidate_registry.service.CandidateService;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/candidates")
public class CandidateApiController {

    private final CandidateService service;

    public CandidateApiController(CandidateService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CandidateDto> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String origin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "external_ref") String sort,
            @RequestParam(defaultValue = "asc") String dir) {
        Sort.Direction d = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(Math.max(0, page), size, Sort.by(d, sort));
        Page<Candidate> pageData = service.search(name, nationality, origin, p);
        return pageData.map(CandidateDto::new);
    }

    @PostMapping("/csv/upload")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file, HttpServletRequest req)
            throws IOException {
        try {
            String baseUrl = getBaseUrl(req);
            CsvUploadResult res = service.uploadCsv(file, baseUrl);
            return ResponseEntity.ok(res);
        } catch (CandidateService.FileSizeLimitExceededException ex) {
            Map<String, String> body = Map.of("error", ex.code, "message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
        } catch (RuntimeException ex) {
            Map<String, String> body = Map.of("error", "MALFORMED_CSV", "message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    @GetMapping("/csv/upload/errors/{filename:.+}")
    public ResponseEntity<?> downloadErrorReport(@PathVariable String filename) throws IOException {
        String decoded = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        File f = service.getErrorReportFile(decoded);
        if (f == null)
            return ResponseEntity.notFound().build();
        InputStreamResource resource = new InputStreamResource(new FileInputStream(f));
        String ct = "text/csv; charset=utf-8";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + decoded + "\"")
                .contentType(MediaType.parseMediaType(ct))
                .body(resource);
    }

    @GetMapping("/csv/download")
    public ResponseEntity<?> downloadCsv(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String origin) throws IOException {
        // fetch all matching (no paging)
        Pageable p = Pageable.unpaged();
        List<Candidate> list = service.search(name, nationality, origin, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.writeCsvAll(list, baos);
        byte[] bytes = baos.toByteArray();
        String filename = "candidate_export_"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(bytes);
    }

    private String getBaseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        int port = req.getServerPort();
        String host = req.getServerName();
        String base = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port);
        return base;
    }

    // helper class for resource streaming
    static class InputStreamResource extends org.springframework.core.io.InputStreamResource {
        public InputStreamResource(InputStream inputStream) {
            super(inputStream);
        }
    }
}
