package com.studyai.course;

import com.studyai.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Collection of documents/chapters attached to this course
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseDocument> documents = new ArrayList<>();

    // Legacy fields preserved for backward compatibility and non-destructive transition
    @Column(name = "document_filename", length = 255)
    private String documentFilename;

    @Column(name = "document_storage_path", length = 500)
    private String documentStoragePath;

    @Column(name = "document_file_size")
    private Long documentFileSize;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "document_extracted_text", columnDefinition = "LONGTEXT")
    private String documentExtractedText;

    @Column(name = "document_uploaded_at")
    private LocalDateTime documentUploadedAt;

    public Course() {
    }

    public Course(String title, String description, User user) {
        this.title = title;
        this.description = description;
        this.user = user;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<CourseDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<CourseDocument> documents) {
        this.documents = documents;
    }

    public void addDocument(CourseDocument document) {
        if (documents == null) {
            documents = new ArrayList<>();
        }
        documents.add(document);
        document.setCourse(this);
    }

    public void removeDocument(CourseDocument document) {
        if (documents != null) {
            documents.remove(document);
            document.setCourse(null);
        }
    }

    public int getDocumentsCount() {
        return documents != null ? documents.size() : 0;
    }

    public boolean hasDocument() {
        return getDocumentsCount() > 0 || (documentStoragePath != null && !documentStoragePath.isBlank());
    }

    // Legacy document helpers
    public void clearLegacyDocument() {
        this.documentFilename = null;
        this.documentStoragePath = null;
        this.documentFileSize = null;
        this.documentContentType = null;
        this.documentExtractedText = null;
        this.documentUploadedAt = null;
    }

    public String getDocumentFilename() {
        return documentFilename;
    }

    public void setDocumentFilename(String documentFilename) {
        this.documentFilename = documentFilename;
    }

    public String getDocumentStoragePath() {
        return documentStoragePath;
    }

    public void setDocumentStoragePath(String documentStoragePath) {
        this.documentStoragePath = documentStoragePath;
    }

    public Long getDocumentFileSize() {
        return documentFileSize;
    }

    public void setDocumentFileSize(Long documentFileSize) {
        this.documentFileSize = documentFileSize;
    }

    public String getDocumentContentType() {
        return documentContentType;
    }

    public void setDocumentContentType(String documentContentType) {
        this.documentContentType = documentContentType;
    }

    public String getDocumentExtractedText() {
        return documentExtractedText;
    }

    public void setDocumentExtractedText(String documentExtractedText) {
        this.documentExtractedText = documentExtractedText;
    }

    public LocalDateTime getDocumentUploadedAt() {
        return documentUploadedAt;
    }

    public void setDocumentUploadedAt(LocalDateTime documentUploadedAt) {
        this.documentUploadedAt = documentUploadedAt;
    }
}
