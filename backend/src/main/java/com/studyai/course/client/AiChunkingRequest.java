package com.studyai.course.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AiChunkingRequest {

    @JsonProperty("document_id")
    private Long documentId;

    @JsonProperty("pages")
    private List<AiPageInput> pages = new ArrayList<>();

    @JsonProperty("text")
    private String text;

    @JsonProperty("target_chunk_size")
    private int targetChunkSize = 800;

    @JsonProperty("chunk_overlap")
    private int chunkOverlap = 120;

    public AiChunkingRequest() {
    }

    public AiChunkingRequest(Long documentId, List<AiPageInput> pages, String text, int targetChunkSize, int chunkOverlap) {
        this.documentId = documentId;
        this.pages = pages != null ? pages : new ArrayList<>();
        this.text = text;
        this.targetChunkSize = targetChunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public List<AiPageInput> getPages() {
        return pages;
    }

    public void setPages(List<AiPageInput> pages) {
        this.pages = pages;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTargetChunkSize() {
        return targetChunkSize;
    }

    public void setTargetChunkSize(int targetChunkSize) {
        this.targetChunkSize = targetChunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
