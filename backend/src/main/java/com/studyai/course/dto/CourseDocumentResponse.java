package com.studyai.course.dto;

import com.studyai.course.Course;
import com.studyai.course.CourseDocument;
import com.studyai.course.ProcessingStatus;

import java.time.LocalDateTime;

public class CourseDocumentResponse {

    private Long id;
    private Long courseId;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private LocalDateTime uploadedAt;
    private String extractedText;
    private ProcessingStatus processingStatus;
    private String processingError;
    private Integer chunksCount;

    public CourseDocumentResponse() {
    }

    public CourseDocumentResponse(Long id, Long courseId, String originalFilename, Long fileSize,
                                  String contentType, LocalDateTime uploadedAt, String extractedText,
                                  ProcessingStatus processingStatus, String processingError, Integer chunksCount) {
        this.id = id;
        this.courseId = courseId;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.uploadedAt = uploadedAt;
        this.extractedText = extractedText;
        this.processingStatus = processingStatus;
        this.processingError = processingError;
        this.chunksCount = chunksCount != null ? chunksCount : 0;
    }

    public static CourseDocumentResponse fromEntity(CourseDocument document) {
        if (document == null) {
            return null;
        }
        return new CourseDocumentResponse(
                document.getId(),
                document.getCourse() != null ? document.getCourse().getId() : null,
                document.getOriginalFilename(),
                document.getFileSize(),
                document.getContentType(),
                document.getUploadedAt(),
                document.getExtractedText(),
                document.getProcessingStatus(),
                document.getProcessingError(),
                document.getChunksCount()
        );
    }

    public static CourseDocumentResponse fromEntitySummary(CourseDocument document) {
        if (document == null) {
            return null;
        }
        return new CourseDocumentResponse(
                document.getId(),
                document.getCourse() != null ? document.getCourse().getId() : null,
                document.getOriginalFilename(),
                document.getFileSize(),
                document.getContentType(),
                document.getUploadedAt(),
                null, // Do not return heavy extracted text in list endpoints
                document.getProcessingStatus(),
                document.getProcessingError(),
                document.getChunksCount()
        );
    }

    public static CourseDocumentResponse fromCourse(Course course) {
        if (course == null) {
            return null;
        }
        // If course has modern documents, return the most recent one
        if (course.getDocuments() != null && !course.getDocuments().isEmpty()) {
            return fromEntity(course.getDocuments().get(course.getDocuments().size() - 1));
        }
        if (!course.hasDocument()) {
            return null;
        }
        return new CourseDocumentResponse(
                null,
                course.getId(),
                course.getDocumentFilename(),
                course.getDocumentFileSize(),
                course.getDocumentContentType(),
                course.getDocumentUploadedAt(),
                course.getDocumentExtractedText(),
                ProcessingStatus.PENDING,
                null,
                0
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public Integer getChunksCount() {
        return chunksCount != null ? chunksCount : 0;
    }

    public void setChunksCount(Integer chunksCount) {
        this.chunksCount = chunksCount;
    }
}
