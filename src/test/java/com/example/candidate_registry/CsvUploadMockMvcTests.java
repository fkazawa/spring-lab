package com.example.candidate_registry;

import com.example.candidate_registry.repository.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CsvUploadMockMvcTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    CandidateRepository repo;

    @BeforeEach
    public void before() {
        repo.deleteAll();
    }

    @Test
    public void upload_normal_success() throws Exception {
        String csv = "external_ref,name,age,nationality,origin,notes\n" +
                "CND-001,Jane Smith,31,Canada,Toronto,Has management experience\n";
        MockMultipartFile file = new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/candidates/csv/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"successCount\":1")));
    }

    @Test
    public void upload_partial_failure() throws Exception {
        String csv = "external_ref,name,age,nationality,origin,notes\n" +
                "CND-001,Jane Smith,31,Canada,Toronto,OK\n" +
                ",MissingName,25,Japan,Osaka,No extref\n";
        MockMultipartFile file = new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/candidates/csv/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"successCount\":1")))
                .andExpect(content().string(containsString("\"failureCount\":1")));
    }

    @Test
    public void upload_dup_in_file() throws Exception {
        String csv = "external_ref,name,age,nationality,origin,notes\n" +
                "CND-001,Jane,30,Japan,Tokyo,OK\n" +
                "CND-001,John,28,USA,NY,OK\n";
        MockMultipartFile file = new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/candidates/csv/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"failureCount\":2")));
    }

    @Test
    public void upload_malformed_csv() throws Exception {
        // broken quoting
        String csv = "external_ref,name,age\n" +
                "CND-001,\"Bad quote,31\n";
        MockMultipartFile file = new MockMultipartFile("file", "c.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/api/candidates/csv/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("MALFORMED_CSV")));
    }
}
