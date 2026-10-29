package com.studyai.course.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiChunkItem {

    @JsonProperty("chunk_index")
    private int chunkIndex;

    @JsonProperty("content")
    private String content;

    @JsonProperty("page_number")
    private Integer pageNumber;

    @JsonProperty("char_count")
    private int charCount;

    public AiChunkItem() {
    }

    public AiChunkItem(int chunkIndex, String content, Integer pageNumber, int charCount) {
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.pageNumber = pageNumber;
        this.charCount = charCount;
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
}
