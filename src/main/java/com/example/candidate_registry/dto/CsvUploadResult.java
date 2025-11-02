package com.example.candidate_registry.dto;

import java.util.List;
import java.util.Map;

public class CsvUploadResult {
    public int totalRows;
    public int successCount;
    public int failureCount;
    public List<Map<String, String>> warnings;
    public ErrorReport errorReport;

    public static class ErrorReport {
        public boolean available;
        public String downloadUrl;
    }
}
