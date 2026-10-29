package com.studyai.course.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AiChunkingResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("is_exploitable")
    private boolean isExploitable;

    @JsonProperty("total_chunks")
    private int totalChunks;

    @JsonProperty("chunks")
    private List<AiChunkItem> chunks = new ArrayList<>();

    @JsonProperty("error_message")
    private String errorMessage;

    public AiChunkingResponse() {
    }

    public AiChunkingResponse(String status, boolean isExploitable, int totalChunks, List<AiChunkItem> chunks, String errorMessage) {
        this.status = status;
        this.isExploitable = isExploitable;
        this.totalChunks = totalChunks;
        this.chunks = chunks != null ? chunks : new ArrayList<>();
        this.errorMessage = errorMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isExploitable() {
        return isExploitable;
    }

    public void setExploitable(boolean exploitable) {
        isExploitable = exploitable;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public List<AiChunkItem> getChunks() {
        return chunks;
    }

    public void setChunks(List<AiChunkItem> chunks) {
        this.chunks = chunks;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
