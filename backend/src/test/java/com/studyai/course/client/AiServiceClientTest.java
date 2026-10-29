package com.studyai.course.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(AiServiceClient.class)
@ActiveProfiles("test")
class AiServiceClientTest {

    @Autowired
    private AiServiceClient aiServiceClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    @DisplayName("Should send chunking request to FastAPI and parse structured response")
    void shouldSendRequestAndParseResponse() {
        String jsonResponse = """
                {
                    "status": "COMPLETED",
                    "is_exploitable": true,
                    "total_chunks": 2,
                    "chunks": [
                        {
                            "chunk_index": 0,
                            "content": "Contenu du premier chunk",
                            "page_number": 1,
                            "char_count": 24
                        },
                        {
                            "chunk_index": 1,
                            "content": "Contenu du second chunk",
                            "page_number": 2,
                            "char_count": 23
                        }
                    ],
                    "error_message": null
                }
                """;

        server.expect(requestTo("http://localhost:8000/api/v1/chunking/process"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        AiChunkingRequest request = new AiChunkingRequest(
                10L,
                List.of(new AiPageInput(1, "Page 1"), new AiPageInput(2, "Page 2")),
                null,
                800,
                120
        );

        AiChunkingResponse response = aiServiceClient.processChunking(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.isExploitable()).isTrue();
        assertThat(response.getTotalChunks()).isEqualTo(2);
        assertThat(response.getChunks()).hasSize(2);
        assertThat(response.getChunks().get(0).getContent()).isEqualTo("Contenu du premier chunk");
        assertThat(response.getChunks().get(0).getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw AiServiceException when FastAPI returns HTTP 500 internal error")
    void shouldThrowAiServiceExceptionOn500() {
        server.expect(requestTo("http://localhost:8000/api/v1/chunking/process"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Internal server failure\"}"));

        AiChunkingRequest request = new AiChunkingRequest(5L, List.of(), "Short text", 800, 120);

        assertThatThrownBy(() -> aiServiceClient.processChunking(request))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Le service IA a rejeté la requête");
    }

    @Test
    @DisplayName("Should check service health status")
    void shouldCheckHealthStatus() {
        server.expect(requestTo("http://localhost:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        assertThat(aiServiceClient.isHealthy()).isTrue();
    }
}
