package com.studyai.course;

import com.studyai.common.exception.ResourceNotFoundException;
import com.studyai.course.dto.CourseDocumentResponse;
import com.studyai.course.dto.CourseRequest;
import com.studyai.course.dto.CourseResponse;
import com.studyai.user.Role;
import com.studyai.user.User;
import com.studyai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private CourseDocumentRepository courseDocumentRepository;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @InjectMocks
    private CourseService courseService;


    private User studentA;
    private User studentB;
    private Course courseA;

    @BeforeEach
    void setUp() {
        studentA = new User("Alice Dupont", "alice@studyai.test", "hashPasswordA", Role.STUDENT);
        studentA.setId(1L);

        studentB = new User("Bob Martin", "bob@studyai.test", "hashPasswordB", Role.STUDENT);
        studentB.setId(2L);

        courseA = new Course("Mathématiques Appliquées", "Cours d'algèbre et analyse", studentA);
        courseA.setId(10L);
    }

    @Test
    @DisplayName("Should successfully create a course for the authenticated student")
    void shouldCreateCourse() {
        CourseRequest request = new CourseRequest("Mathématiques Appliquées", "Cours d'algèbre et analyse");
        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.save(any(Course.class))).thenReturn(courseA);
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(courseA));

        CourseResponse response = courseService.createCourse(request, "alice@studyai.test");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getDisplayNumber()).isEqualTo(1);
        assertThat(response.getTitle()).isEqualTo("Mathématiques Appliquées");
        assertThat(response.getDescription()).isEqualTo("Cours d'algèbre et analyse");
        assertThat(response.getUserId()).isEqualTo(1L);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("Should list all courses belonging to the authenticated student with consecutive display numbers")
    void shouldListCoursesForStudent() {
        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(courseA));

        List<CourseResponse> responses = courseService.getCourses("alice@studyai.test");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTitle()).isEqualTo("Mathématiques Appliquées");
        assertThat(responses.get(0).getDisplayNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should retrieve course by ID when user is the owner with its correct displayNumber")
    void shouldGetCourseByIdWhenOwner() {
        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(courseA));

        CourseResponse response = courseService.getCourseById(10L, "alice@studyai.test");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getDisplayNumber()).isEqualTo(1);
        assertThat(response.getTitle()).isEqualTo("Mathématiques Appliquées");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when student attempts to get another student's course")
    void shouldThrowWhenGettingAnotherStudentsCourse() {
        when(userRepository.findByEmail("bob@studyai.test")).thenReturn(Optional.of(studentB));
        when(courseRepository.findByIdAndUser(10L, studentB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseById(10L, "bob@studyai.test"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cours introuvable");
    }

    @Test
    @DisplayName("Should update course when user is the owner")
    void shouldUpdateCourseWhenOwner() {
        CourseRequest updateRequest = new CourseRequest("Maths Avancées", "Analyse numérique");
        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseRepository.save(any(Course.class))).thenReturn(courseA);
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(courseA));

        CourseResponse response = courseService.updateCourse(10L, updateRequest, "alice@studyai.test");

        assertThat(response).isNotNull();
        assertThat(response.getDisplayNumber()).isEqualTo(1);
        assertThat(courseA.getTitle()).isEqualTo("Maths Avancées");
        assertThat(courseA.getDescription()).isEqualTo("Analyse numérique");
        verify(courseRepository).save(courseA);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when student attempts to update another student's course")
    void shouldThrowWhenUpdatingAnotherStudentsCourse() {
        CourseRequest updateRequest = new CourseRequest("Hack", "Tentative de modification");
        when(userRepository.findByEmail("bob@studyai.test")).thenReturn(Optional.of(studentB));
        when(courseRepository.findByIdAndUser(10L, studentB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(10L, updateRequest, "bob@studyai.test"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cours introuvable");
    }

    @Test
    @DisplayName("Should delete course when user is the owner")
    void shouldDeleteCourseWhenOwner() {
        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));

        courseService.deleteCourse(10L, "alice@studyai.test");

        verify(courseRepository).delete(courseA);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when student attempts to delete another student's course")
    void shouldThrowWhenDeletingAnotherStudentsCourse() {
        when(userRepository.findByEmail("bob@studyai.test")).thenReturn(Optional.of(studentB));
        when(courseRepository.findByIdAndUser(10L, studentB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(10L, "bob@studyai.test"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cours introuvable");
    }

    @Test
    @DisplayName("Should add a PDF document to a course and extract text")
    void shouldAddDocumentToCourse() {
        MockMultipartFile file = new MockMultipartFile("file", "algebre.pdf", "application/pdf", "%PDF-sample".getBytes());
        Path savedPath = Paths.get("uploads/documents/test-uuid.pdf");
        CourseDocument doc = new CourseDocument(courseA, "algebre.pdf", savedPath.toString(), (long) "%PDF-sample".length(), "application/pdf", "Contenu extrait du cours d'algèbre");
        doc.setId(101L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(documentService.storeFile(file)).thenReturn(savedPath);
        when(documentService.extractText(savedPath)).thenReturn("Contenu extrait du cours d'algèbre");
        when(courseDocumentRepository.save(any(CourseDocument.class))).thenReturn(doc);
        when(documentProcessingService.processDocument(any(CourseDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDocumentResponse response = courseService.addDocument(10L, file, "alice@studyai.test");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getOriginalFilename()).isEqualTo("algebre.pdf");
        assertThat(response.getExtractedText()).isEqualTo("Contenu extrait du cours d'algèbre");
        verify(courseDocumentRepository).save(any(CourseDocument.class));
        verify(documentProcessingService).processDocument(any(CourseDocument.class));
    }

    @Test
    @DisplayName("Should reprocess an existing document")
    void shouldReprocessDocument() {
        CourseDocument doc = new CourseDocument(courseA, "algebre.pdf", "path.pdf", 100L, "application/pdf", "txt");
        doc.setId(101L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseDocumentRepository.findByIdAndCourse(101L, courseA)).thenReturn(Optional.of(doc));
        when(documentProcessingService.processDocument(doc)).thenReturn(doc);

        CourseDocumentResponse response = courseService.reprocessDocument(10L, 101L, "alice@studyai.test");

        assertThat(response).isNotNull();
        verify(documentProcessingService).processDocument(doc);
    }

    @Test
    @DisplayName("Should get chunks for a document")
    void shouldGetDocumentChunks() {
        CourseDocument doc = new CourseDocument(courseA, "algebre.pdf", "path.pdf", 100L, "application/pdf", "txt");
        doc.setId(101L);
        DocumentChunk chunk1 = new DocumentChunk(doc, 0, "Chunk 1 content", 1);
        chunk1.setId(1L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseDocumentRepository.findByIdAndCourse(101L, courseA)).thenReturn(Optional.of(doc));
        when(documentChunkRepository.findByDocumentOrderByChunkIndexAsc(doc)).thenReturn(List.of(chunk1));

        List<com.studyai.course.dto.DocumentChunkResponse> chunks = courseService.getDocumentChunks(10L, 101L, "alice@studyai.test");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("Chunk 1 content");
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should list documents of a course")
    void shouldListCourseDocuments() {
        CourseDocument doc1 = new CourseDocument(courseA, "chap1.pdf", "p1", 100L, "application/pdf", "T1");
        doc1.setId(1L);
        CourseDocument doc2 = new CourseDocument(courseA, "chap2.pdf", "p2", 200L, "application/pdf", "T2");
        doc2.setId(2L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseDocumentRepository.findByCourseOrderByUploadedAtDesc(courseA)).thenReturn(List.of(doc2, doc1));

        List<CourseDocumentResponse> docs = courseService.getCourseDocuments(10L, "alice@studyai.test");

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getOriginalFilename()).isEqualTo("chap2.pdf");
        assertThat(docs.get(1).getOriginalFilename()).isEqualTo("chap1.pdf");
    }

    @Test
    @DisplayName("Should delete individual course document and remove physical file")
    void shouldDeleteCourseDocument() {
        CourseDocument doc = new CourseDocument(courseA, "algebre.pdf", "uploads/documents/test-uuid.pdf", 150L, "application/pdf", "txt");
        doc.setId(55L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByIdAndUser(10L, studentA)).thenReturn(Optional.of(courseA));
        when(courseDocumentRepository.findByIdAndCourse(55L, courseA)).thenReturn(Optional.of(doc));

        courseService.deleteCourseDocument(10L, 55L, "alice@studyai.test");

        verify(documentService).deletePhysicalFile("uploads/documents/test-uuid.pdf");
        verify(courseDocumentRepository).delete(doc);
    }

    @Test
    @DisplayName("Should assign sequential display numbers 1..N and renumber consecutively after deletion")
    void shouldAssignSequentialDisplayNumbersAndRenumberOnDelete() {
        Course c1 = new Course("Cours A", "Desc A", studentA);
        c1.setId(101L);
        Course c2 = new Course("Cours B", "Desc B", studentA);
        c2.setId(102L);
        Course c3 = new Course("Cours C", "Desc C", studentA);
        c3.setId(103L);

        when(userRepository.findByEmail("alice@studyai.test")).thenReturn(Optional.of(studentA));
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(c1, c2, c3));

        List<CourseResponse> initialList = courseService.getCourses("alice@studyai.test");
        assertThat(initialList).hasSize(3);
        assertThat(initialList.get(0).getDisplayNumber()).isEqualTo(1);
        assertThat(initialList.get(1).getDisplayNumber()).isEqualTo(2);
        assertThat(initialList.get(2).getDisplayNumber()).isEqualTo(3);

        // Simulate deletion of c2 (Cours 2): remaining courses are c1 and c3
        when(courseRepository.findByIdAndUser(102L, studentA)).thenReturn(Optional.of(c2));
        when(courseDocumentRepository.findByCourseOrderByUploadedAtDesc(c2)).thenReturn(List.of());
        courseService.deleteCourse(102L, "alice@studyai.test");
        verify(courseRepository).delete(c2);

        // After deletion, remaining courses c1 and c3 are renumbered 1 and 2
        when(courseRepository.findByUserOrderByCreatedAtAscIdAsc(studentA)).thenReturn(List.of(c1, c3));
        List<CourseResponse> afterDeleteList = courseService.getCourses("alice@studyai.test");
        assertThat(afterDeleteList).hasSize(2);
        assertThat(afterDeleteList.get(0).getId()).isEqualTo(101L);
        assertThat(afterDeleteList.get(0).getDisplayNumber()).isEqualTo(1);
        assertThat(afterDeleteList.get(1).getId()).isEqualTo(103L);
        assertThat(afterDeleteList.get(1).getDisplayNumber()).isEqualTo(2);
    }
}

