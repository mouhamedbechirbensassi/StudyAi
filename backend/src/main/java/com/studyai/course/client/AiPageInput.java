package com.studyai.course.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiPageInput(
        @JsonProperty("page_number") Integer pageNumber,
        @JsonProperty("text") String text
) {
}
