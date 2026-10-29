package com.studyai.course;

import com.studyai.common.exception.ResourceNotFoundException;
import com.studyai.course.dto.CourseDocumentResponse;
import com.studyai.course.dto.CourseRequest;
import com.studyai.course.dto.CourseResponse;
import com.studyai.course.dto.DocumentChunkResponse;
import com.studyai.user.User;
import com.studyai.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final CourseDocumentRepository courseDocumentRepository;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentChunkRepository documentChunkRepository;

    public CourseService(CourseRepository courseRepository,
                         UserRepository userRepository,
                         DocumentService documentService,
                         CourseDocumentRepository courseDocumentRepository,
                         DocumentProcessingService documentProcessingService,
                         DocumentChunkRepository documentChunkRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.documentService = documentService;
        this.courseDocumentRepository = courseDocumentRepository;
        this.documentProcessingService = documentProcessingService;
        this.documentChunkRepository = documentChunkRepository;
    }

    public CourseResponse createCourse(CourseRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        Course course = new Course(
                request.getTitle().trim(),
                request.getDescription() != null ? request.getDescription().trim() : null,
                user
        );

        Course saved = courseRepository.save(course);
        int displayNumber = calculateDisplayNumber(saved, user);
        return CourseResponse.fromEntity(saved, displayNumber);
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Course> coursesAsc = courseRepository.findByUserOrderByCreatedAtAscIdAsc(user);
        List<CourseResponse> responses = new java.util.ArrayList<>();
        for (int i = 0; i < coursesAsc.size(); i++) {
            responses.add(CourseResponse.fromEntity(coursesAsc.get(i), i + 1));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourseById(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));
        int displayNumber = calculateDisplayNumber(course, user);
        return CourseResponse.fromEntity(course, displayNumber);
    }

    public CourseResponse updateCourse(Long id, CourseRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        course.setTitle(request.getTitle().trim());
        course.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Course updated = courseRepository.save(course);
        int displayNumber = calculateDisplayNumber(updated, user);
        return CourseResponse.fromEntity(updated, displayNumber);
    }

    private int calculateDisplayNumber(Course course, User user) {
        List<Course> userCourses = courseRepository.findByUserOrderByCreatedAtAscIdAsc(user);
        for (int i = 0; i < userCourses.size(); i++) {
            if (userCourses.get(i).getId() != null && userCourses.get(i).getId().equals(course.getId())) {
                return i + 1;
            }
        }
        return userCourses.size();
    }

    public void deleteCourse(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        // Delete all physical files of documents associated with this course
        List<CourseDocument> documents = courseDocumentRepository.findByCourseOrderByUploadedAtDesc(course);
        for (CourseDocument doc : documents) {
            documentService.deletePhysicalFile(doc.getStoragePath());
        }

        // Also clean up any legacy storage path if present
        if (course.getDocumentStoragePath() != null) {
            documentService.deletePhysicalFile(course.getDocumentStoragePath());
        }

        courseRepository.delete(course);
    }

    // --- Multi-Document Management Endpoints ---

    public CourseDocumentResponse addDocument(Long courseId, MultipartFile file, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        // Store new physical file and extract text (validates extension, magic bytes, size, etc.)
        Path targetPath = documentService.storeFile(file);
        String extractedText = documentService.extractText(targetPath);

        CourseDocument document = new CourseDocument(
                course,
                file.getOriginalFilename(),
                targetPath.toString(),
                file.getSize(),
                file.getContentType(),
                extractedText
        );

        CourseDocument savedDoc = courseDocumentRepository.save(document);
        course.addDocument(savedDoc);

        // Automatically trigger RAG pipeline (cleaning, chunking, status tracking)
        savedDoc = documentProcessingService.processDocument(savedDoc);

        return CourseDocumentResponse.fromEntity(savedDoc);
    }

    public CourseDocumentResponse reprocessDocument(Long courseId, Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        CourseDocument document = courseDocumentRepository.findByIdAndCourse(documentId, course)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        CourseDocument processed = documentProcessingService.processDocument(document);
        return CourseDocumentResponse.fromEntity(processed);
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> getDocumentChunks(Long courseId, Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        CourseDocument document = courseDocumentRepository.findByIdAndCourse(documentId, course)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        return documentChunkRepository.findByDocumentOrderByChunkIndexAsc(document)
                .stream()
                .map(DocumentChunkResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseDocumentResponse> getCourseDocuments(Long courseId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        return courseDocumentRepository.findByCourseOrderByUploadedAtDesc(course)
                .stream()
                .map(CourseDocumentResponse::fromEntitySummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDocumentResponse getCourseDocument(Long courseId, Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        CourseDocument document = courseDocumentRepository.findByIdAndCourse(documentId, course)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        return CourseDocumentResponse.fromEntity(document);
    }

    public void deleteCourseDocument(Long courseId, Long documentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        CourseDocument document = courseDocumentRepository.findByIdAndCourse(documentId, course)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable"));

        documentService.deletePhysicalFile(document.getStoragePath());
        course.removeDocument(document);
        courseDocumentRepository.delete(document);
    }

    // --- Legacy Endpoints Compatibility ---

    public CourseDocumentResponse attachDocument(Long courseId, MultipartFile file, String userEmail) {
        return addDocument(courseId, file, userEmail);
    }

    @Transactional(readOnly = true)
    public CourseDocumentResponse getCourseDocument(Long courseId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        List<CourseDocument> docs = courseDocumentRepository.findByCourseOrderByUploadedAtDesc(course);
        if (!docs.isEmpty()) {
            return CourseDocumentResponse.fromEntity(docs.get(0));
        }

        if (course.hasDocument()) {
            return CourseDocumentResponse.fromCourse(course);
        }

        throw new ResourceNotFoundException("Aucun document n'est associé à ce cours.");
    }

    public void deleteCourseDocument(Long courseId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Course course = courseRepository.findByIdAndUser(courseId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable"));

        List<CourseDocument> docs = courseDocumentRepository.findByCourseOrderByUploadedAtDesc(course);
        for (CourseDocument doc : docs) {
            documentService.deletePhysicalFile(doc.getStoragePath());
            course.removeDocument(doc);
            courseDocumentRepository.delete(doc);
        }

        if (course.getDocumentStoragePath() != null) {
            documentService.deletePhysicalFile(course.getDocumentStoragePath());
            course.clearLegacyDocument();
            courseRepository.save(course);
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec l'email: " + email));
    }
}
