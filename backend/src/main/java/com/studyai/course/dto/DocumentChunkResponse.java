package com.studyai.course.dto;

import com.studyai.course.DocumentChunk;

import java.time.LocalDateTime;

public class DocumentChunkResponse {

    private Long id;
    private Long documentId;
    private int chunkIndex;
    private String content;
    private Integer pageNumber;
    private int charCount;
    private LocalDateTime createdAt;

    public DocumentChunkResponse() {
    }

    public DocumentChunkResponse(Long id, Long documentId, int chunkIndex, String content,
                                 Integer pageNumber, int charCount, LocalDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.pageNumber = pageNumber;
        this.charCount = charCount;
        this.createdAt = createdAt;
    }

    public static DocumentChunkResponse fromEntity(DocumentChunk chunk) {
        if (chunk == null) {
            return null;
        }
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocument() != null ? chunk.getDocument().getId() : null,
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getPageNumber(),
                chunk.getCharCount(),
                chunk.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
