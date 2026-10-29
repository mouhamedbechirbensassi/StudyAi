package com.studyai.course.dto;

import com.studyai.course.Course;

import java.time.LocalDateTime;

public class CourseResponse {

    private Long id;
    private Integer displayNumber;
    private String title;
    private String description;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int documentsCount;
    private boolean hasDocument;
    private String documentFilename;
    private Long documentFileSize;
    private LocalDateTime documentUploadedAt;

    public CourseResponse() {
    }

    public CourseResponse(Long id, String title, String description, Long userId,
                          LocalDateTime createdAt, LocalDateTime updatedAt,
                          int documentsCount, boolean hasDocument, String documentFilename,
                          Long documentFileSize, LocalDateTime documentUploadedAt) {
        this(id, null, title, description, userId, createdAt, updatedAt, documentsCount, hasDocument, documentFilename, documentFileSize, documentUploadedAt);
    }

    public CourseResponse(Long id, Integer displayNumber, String title, String description, Long userId,
                          LocalDateTime createdAt, LocalDateTime updatedAt,
                          int documentsCount, boolean hasDocument, String documentFilename,
                          Long documentFileSize, LocalDateTime documentUploadedAt) {
        this.id = id;
        this.displayNumber = displayNumber;
        this.title = title;
        this.description = description;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.documentsCount = documentsCount;
        this.hasDocument = hasDocument;
        this.documentFilename = documentFilename;
        this.documentFileSize = documentFileSize;
        this.documentUploadedAt = documentUploadedAt;
    }

    public static CourseResponse fromEntity(Course course) {
        return fromEntity(course, null);
    }

    public static CourseResponse fromEntity(Course course, Integer displayNumber) {
        if (course == null) {
            return null;
        }

        int count = course.getDocumentsCount();
        boolean hasDocs = count > 0 || course.hasDocument();
        String filename = course.getDocumentFilename();
        Long fileSize = course.getDocumentFileSize();
        LocalDateTime uploadedAt = course.getDocumentUploadedAt();

        if (course.getDocuments() != null && !course.getDocuments().isEmpty()) {
            var latest = course.getDocuments().get(course.getDocuments().size() - 1);
            filename = latest.getOriginalFilename();
            fileSize = latest.getFileSize();
            uploadedAt = latest.getUploadedAt();
        }

        return new CourseResponse(
                course.getId(),
                displayNumber,
                course.getTitle(),
                course.getDescription(),
                course.getUser() != null ? course.getUser().getId() : null,
                course.getCreatedAt(),
                course.getUpdatedAt(),
                count,
                hasDocs,
                filename,
                fileSize,
                uploadedAt
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getDisplayNumber() {
        return displayNumber;
    }

    public void setDisplayNumber(Integer displayNumber) {
        this.displayNumber = displayNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getDocumentsCount() {
        return documentsCount;
    }

    public void setDocumentsCount(int documentsCount) {
        this.documentsCount = documentsCount;
    }

    public boolean isHasDocument() {
        return hasDocument;
    }

    public void setHasDocument(boolean hasDocument) {
        this.hasDocument = hasDocument;
    }

    public String getDocumentFilename() {
        return documentFilename;
    }

    public void setDocumentFilename(String documentFilename) {
        this.documentFilename = documentFilename;
    }

    public Long getDocumentFileSize() {
        return documentFileSize;
    }

    public void setDocumentFileSize(Long documentFileSize) {
        this.documentFileSize = documentFileSize;
    }

    public LocalDateTime getDocumentUploadedAt() {
        return documentUploadedAt;
    }

    public void setDocumentUploadedAt(LocalDateTime documentUploadedAt) {
        this.documentUploadedAt = documentUploadedAt;
    }
}
