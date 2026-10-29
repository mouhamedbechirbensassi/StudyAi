package com.studyai.integration;

import com.studyai.auth.AuthService;
import com.studyai.auth.dto.AuthResponse;
import com.studyai.auth.dto.RegisterRequest;
import com.studyai.course.Course;
import com.studyai.course.CourseRepository;
import com.studyai.user.User;
import com.studyai.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiDocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    private String tokenStudentA;
    private String tokenStudentB;
    private Long courseIdA;

    private byte[] createSamplePdfBytes(String textContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(textContent);
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();
        userRepository.deleteAll();

        AuthResponse respA = authService.register(new RegisterRequest("Alice Multi", "alice_multi@test.com", "Password123!"));
        tokenStudentA = respA.getToken();

        AuthResponse respB = authService.register(new RegisterRequest("Bob Multi", "bob_multi@test.com", "Password123!"));
        tokenStudentB = respB.getToken();

        User userA = userRepository.findById(respA.getUser().getId()).orElseThrow();
        Course course = new Course("Algorithmique Avancée", "Structures de données et graphes", userA);
        courseIdA = courseRepository.save(course).getId();
    }

    @Test
    @DisplayName("Should add multiple PDFs to the same course and preserve both")
    void shouldAddMultiplePdfsToSameCourse() throws Exception {
        byte[] pdf1 = createSamplePdfBytes("Chapitre 1 : Arbres et Graphes");
        MockMultipartFile file1 = new MockMultipartFile("file", "chapitre1.pdf", "application/pdf", pdf1);

        byte[] pdf2 = createSamplePdfBytes("Chapitre 2 : Algorithmes gloutons");
        MockMultipartFile file2 = new MockMultipartFile("file", "chapitre2.pdf", "application/pdf", pdf2);

        // Upload first document
        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/documents")
                        .file(file1)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.originalFilename", is("chapitre1.pdf")))
                .andExpect(jsonPath("$.extractedText", containsString("Arbres et Graphes")));

        // Upload second document to the same course without replacing the first
        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/documents")
                        .file(file2)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.originalFilename", is("chapitre2.pdf")))
                .andExpect(jsonPath("$.extractedText", containsString("Algorithmes gloutons")));

        // List documents: should have size 2
        mockMvc.perform(get("/api/courses/" + courseIdA + "/documents")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Check CourseResponse documentsCount
        mockMvc.perform(get("/api/courses/" + courseIdA)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsCount", is(2)))
                .andExpect(jsonPath("$.hasDocument", is(true)));
    }

    @Test
    @DisplayName("Should delete one document while keeping the other intact")
    void shouldDeleteOneDocumentIndividually() throws Exception {
        byte[] pdf1 = createSamplePdfBytes("Doc A");
        MockMultipartFile file1 = new MockMultipartFile("file", "docA.pdf", "application/pdf", pdf1);

        byte[] pdf2 = createSamplePdfBytes("Doc B");
        MockMultipartFile file2 = new MockMultipartFile("file", "docB.pdf", "application/pdf", pdf2);

        String res1 = mockMvc.perform(multipart("/api/courses/" + courseIdA + "/documents")
                        .file(file1)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/documents")
                        .file(file2)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated());

        // Extract doc1 ID
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res1);
        long doc1Id = jsonNode.get("id").asLong();

        // Delete doc1
        mockMvc.perform(delete("/api/courses/" + courseIdA + "/documents/" + doc1Id)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNoContent());

        // List documents: should now have size 1 (docB remaining)
        mockMvc.perform(get("/api/courses/" + courseIdA + "/documents")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalFilename", is("docB.pdf")));
    }

    @Test
    @DisplayName("Student B cannot access or delete documents from Student A's course (404 Not Found)")
    void shouldEnforceSecurityIsolation() throws Exception {
        byte[] pdf = createSamplePdfBytes("Confidentiel");
        MockMultipartFile file = new MockMultipartFile("file", "secret.pdf", "application/pdf", pdf);

        String res = mockMvc.perform(multipart("/api/courses/" + courseIdA + "/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long docId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res).get("id").asLong();

        // Student B tries to list documents -> 404
        mockMvc.perform(get("/api/courses/" + courseIdA + "/documents")
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());

        // Student B tries to get document -> 404
        mockMvc.perform(get("/api/courses/" + courseIdA + "/documents/" + docId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());

        // Student B tries to delete document -> 404
        mockMvc.perform(delete("/api/courses/" + courseIdA + "/documents/" + docId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }
}
