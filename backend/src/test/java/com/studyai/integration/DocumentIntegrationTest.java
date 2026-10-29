package com.studyai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class DocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        AuthResponse respA = authService.register(new RegisterRequest("Student A", "studentA_doc@test.com", "Password123!"));
        tokenStudentA = respA.getToken();

        AuthResponse respB = authService.register(new RegisterRequest("Student B", "studentB_doc@test.com", "Password123!"));
        tokenStudentB = respB.getToken();

        User userA = userRepository.findById(respA.getUser().getId()).orElseThrow();
        Course course = new Course("Intelligence Artificielle", "Introduction", userA);
        courseIdA = courseRepository.save(course).getId();
    }

    @Test
    @DisplayName("1. Should upload a valid PDF and extract text (200 OK)")
    void shouldUploadPdfAndExtractText() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Introduction aux reseaux neuronaux");
        MockMultipartFile file = new MockMultipartFile(
                "file", "cours-ia.pdf", "application/pdf", pdfBytes
        );

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId", is(courseIdA.intValue())))
                .andExpect(jsonPath("$.originalFilename", is("cours-ia.pdf")))
                .andExpect(jsonPath("$.fileSize", notNullValue()))
                .andExpect(jsonPath("$.extractedText", containsString("Introduction aux reseaux neuronaux")));
    }

    @Test
    @DisplayName("2. Should retrieve document metadata and extracted text (200 OK)")
    void shouldGetDocumentMetadataAndText() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Contenu sauvegarde dans la base");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/courses/" + courseIdA + "/document")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename", is("doc.pdf")))
                .andExpect(jsonPath("$.extractedText", containsString("Contenu sauvegarde dans la base")));
    }

    @Test
    @DisplayName("3. Should reject document upload from unauthenticated user (401 Unauthorized)")
    void shouldRejectUnauthenticatedDocumentUpload() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Test non authentifie");
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document").file(file))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/courses/" + courseIdA + "/document"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Student B should get 404 when reading Student A's document")
    void shouldDenyStudentBReadingDocument() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Document confidentiel");
        MockMultipartFile file = new MockMultipartFile("file", "secret.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/courses/" + courseIdA + "/document")
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. Student B should get 404 when attempting to upload to Student A's course")
    void shouldDenyStudentBUploadingDocument() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Tentative piratage");
        MockMultipartFile file = new MockMultipartFile("file", "hack.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Student B should get 404 when attempting to delete Student A's document")
    void shouldDenyStudentBDeletingDocument() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Document protege");
        MockMultipartFile file = new MockMultipartFile("file", "safe.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/courses/" + courseIdA + "/document")
                        .header("Authorization", "Bearer " + tokenStudentB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Should reject non-PDF file upload (400 Bad Request)")
    void shouldRejectNonPdfFile() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "script.txt", "text/plain", "Ceci est un fichier texte".getBytes()
        );

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(textFile)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(".pdf")));
    }

    @Test
    @DisplayName("8. Should reject empty file upload (400 Bad Request)")
    void shouldRejectEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("vide")));
    }

    @Test
    @DisplayName("9. Should delete document (204 No Content) and subsequent GET returns 404")
    void shouldDeleteDocumentSuccessfully() throws Exception {
        byte[] pdfBytes = createSamplePdfBytes("Document a supprimer");
        MockMultipartFile file = new MockMultipartFile("file", "del.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/courses/" + courseIdA + "/document")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/courses/" + courseIdA + "/document")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseIdA + "/document")
                        .header("Authorization", "Bearer " + tokenStudentA))
                .andExpect(status().isNotFound());
    }
}
