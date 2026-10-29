package com.studyai.integration;

import com.studyai.auth.AuthService;
import com.studyai.auth.dto.AuthResponse;
import com.studyai.auth.dto.RegisterRequest;
import com.studyai.course.Course;
import com.studyai.course.CourseDocument;
import com.studyai.course.CourseDocumentRepository;
import com.studyai.course.CourseRepository;
import com.studyai.course.DocumentChunkRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentChunkingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseDocumentRepository courseDocumentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    private String tokenStudentA;
    private String tokenStudentB;
    private Long courseIdA;

    private byte[] createSamplePdfBytes(String textContent, int pagesCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            for (int p = 1; p <= pagesCount; p++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("Page " + p + " : " + textContent);
                    cs.endText();
                }
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyPdfBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            // Page with no text content stream
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    @BeforeEach
    void setUp() {
        courseRepository.deleteAll();
        userRepository.deleteAll();

        AuthResponse respA = authService.register(new RegisterRequest("Alice Chunk", "alice_chunk@test.com", "Password123!"));
        tokenStudentA = respA.getToken();

        AuthResponse respB = authService.register(new RegisterRequest("Bob Chunk", "bob_chunk@test.com", "Password123!"));
        tokenStudentB = respB.getToken();

        User userA = userRepository.findById(respA.getUser().getId()).orElseThrow();
        Course course = new Course("Intelligence Artificielle & RAG", "Pipeline d'extraction et chunking", userA);
        courseIdA = courseRepository.save(course).getId();
    }

    @Test
    @DisplayName("Should upload PDF and automatically segment text into chunks with COMPLETED status")
    void shouldUploadPdfAndAutomaticallyCreateChunks() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Introduction aux architectures transformers et RAG en intelligence artificielle.", 2);
        MockMultipartFile file = new MockMultipartFile("file", "intro_rag.pdf", "application/pdf", pdfBytes);

        String responseBody = mockMvc.perform(multipart("/api/courses/{courseId}/documents", courseIdA)
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.originalFilename", is("intro_rag.pdf")))
                .andExpect(jsonPath("$.processingStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.chunksCount", greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();

        // Verify chunks exist in repository
        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
        long docId = rootNode.get("id").asLong();

        CourseDocument doc = courseDocumentRepository.findById(docId).orElseThrow();
        long chunkCount = documentChunkRepository.countByDocument(doc);
        assertThat(chunkCount).isGreaterThanOrEqualTo(1);

        // Verify GET /chunks endpoint
        mockMvc.perform(get("/api/courses/{courseId}/documents/{docId}/chunks", courseIdA, docId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize((int) chunkCount)))
                .andExpect(jsonPath("$[0].chunkIndex", is(0)))
                .andExpect(jsonPath("$[0].pageNumber", notNullValue()))
                .andExpect(jsonPath("$[0].charCount", greaterThanOrEqualTo(10)));
    }

    @Test
    @DisplayName("Should handle empty/scanned PDF without text and mark status NO_TEXT with 0 chunks")
    void shouldHandleEmptyOrScannedPdf() throws Exception {
        byte[] emptyPdf = createEmptyPdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", emptyPdf);

        mockMvc.perform(multipart("/api/courses/{courseId}/documents", courseIdA)
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processingStatus", is("NO_TEXT")))
                .andExpect(jsonPath("$.chunksCount", is(0)));
    }

    @Test
    @DisplayName("Should reprocess document cleanly without duplicating chunks")
    void shouldReprocessDocumentCleanly() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Traitement du langage naturel et vectorisation sémantique.", 1);
        MockMultipartFile file = new MockMultipartFile("file", "nlp.pdf", "application/pdf", pdfBytes);

        String uploadResponse = mockMvc.perform(multipart("/api/courses/{courseId}/documents", courseIdA)
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long docId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(uploadResponse).get("id").asLong();
        CourseDocument doc = courseDocumentRepository.findById(docId).orElseThrow();
        long initialCount = documentChunkRepository.countByDocument(doc);

        // Reprocess document
        mockMvc.perform(post("/api/courses/{courseId}/documents/{docId}/process", courseIdA, docId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.chunksCount", is((int) initialCount)));

        // Ensure no duplicate chunks were created
        long countAfterReprocess = documentChunkRepository.countByDocument(doc);
        assertThat(countAfterReprocess).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("Should prevent cross-student access to chunks and reprocessing")
    void shouldPreventCrossStudentAccess() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Sécurité et contrôle d'accès dans les applications multi-tenants.", 1);
        MockMultipartFile file = new MockMultipartFile("file", "security.pdf", "application/pdf", pdfBytes);

        String uploadResponse = mockMvc.perform(multipart("/api/courses/{courseId}/documents", courseIdA)
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long docId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(uploadResponse).get("id").asLong();

        // Student B trying to get chunks of Student A's document -> 404
        mockMvc.perform(get("/api/courses/{courseId}/documents/{docId}/chunks", courseIdA, docId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());

        // Student B trying to reprocess Student A's document -> 404
        mockMvc.perform(post("/api/courses/{courseId}/documents/{docId}/process", courseIdA, docId)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should cascade delete chunks when document is deleted")
    void shouldCascadeDeleteChunksWhenDocumentDeleted() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Cycle de vie des chunks et cascade delete JPA.", 1);
        MockMultipartFile file = new MockMultipartFile("file", "cascade.pdf", "application/pdf", pdfBytes);

        String uploadResponse = mockMvc.perform(multipart("/api/courses/{courseId}/documents", courseIdA)
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long docId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(uploadResponse).get("id").asLong();
        CourseDocument doc = courseDocumentRepository.findById(docId).orElseThrow();
        assertThat(documentChunkRepository.countByDocument(doc)).isGreaterThan(0);

        // Delete document
        mockMvc.perform(delete("/api/courses/{courseId}/documents/{docId}", courseIdA, docId)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNoContent());

        // Document chunks should be deleted
        assertThat(courseDocumentRepository.findById(docId)).isEmpty();
        assertThat(documentChunkRepository.countByDocument(doc)).isZero();
    }
}
