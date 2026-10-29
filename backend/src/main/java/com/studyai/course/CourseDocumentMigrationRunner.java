package com.studyai.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CourseDocumentMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CourseDocumentMigrationRunner.class);

    private final CourseRepository courseRepository;
    private final CourseDocumentRepository courseDocumentRepository;
    private final DocumentProcessingService documentProcessingService;

    public CourseDocumentMigrationRunner(CourseRepository courseRepository,
                                         CourseDocumentRepository courseDocumentRepository,
                                         DocumentProcessingService documentProcessingService) {
        this.courseRepository = courseRepository;
        this.courseDocumentRepository = courseDocumentRepository;
        this.documentProcessingService = documentProcessingService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            List<Course> courses = courseRepository.findAll();
            int migratedCount = 0;

            for (Course course : courses) {
                String legacyPath = course.getDocumentStoragePath();
                if (legacyPath != null && !legacyPath.isBlank()) {
                    boolean alreadyMigrated = courseDocumentRepository.existsByCourseAndStoragePath(course, legacyPath);
                    if (!alreadyMigrated) {
                        CourseDocument doc = new CourseDocument();
                        doc.setCourse(course);
                        doc.setOriginalFilename(course.getDocumentFilename() != null ? course.getDocumentFilename() : "document.pdf");
                        doc.setStoragePath(legacyPath);
                        doc.setFileSize(course.getDocumentFileSize() != null ? course.getDocumentFileSize() : 0L);
                        doc.setContentType(course.getDocumentContentType() != null ? course.getDocumentContentType() : "application/pdf");
                        doc.setExtractedText(course.getDocumentExtractedText());
                        doc.setUploadedAt(course.getDocumentUploadedAt() != null ? course.getDocumentUploadedAt() : (course.getCreatedAt() != null ? course.getCreatedAt() : LocalDateTime.now()));
                        doc.setProcessingStatus(ProcessingStatus.PENDING);

                        courseDocumentRepository.save(doc);
                        migratedCount++;
                        log.info("Migration: Ancien document conservé et migré pour le cours #{} ({})", course.getId(), doc.getOriginalFilename());
                    }
                }
            }

            if (migratedCount > 0) {
                log.info("Migration terminée: {} document(s) hérité(s) migré(s) avec succès dans course_documents.", migratedCount);
            }

            // Ensure any existing documents that were not processed are processed into chunks
            List<CourseDocument> allDocs = courseDocumentRepository.findAll();
            int processedCount = 0;
            for (CourseDocument doc : allDocs) {
                if (doc.getProcessingStatus() == null || doc.getProcessingStatus() == ProcessingStatus.PENDING) {
                    documentProcessingService.processDocument(doc);
                    processedCount++;
                }
            }

            if (processedCount > 0) {
                log.info("RAG Pipeline: {} document(s) existant(s) préparé(s) et segmenté(s) en chunks.", processedCount);
            }

        } catch (Exception e) {
            log.warn("Erreur mineure lors de la vérification de la migration des documents: {}", e.getMessage());
        }
    }
}
