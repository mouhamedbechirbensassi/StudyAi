package com.studyai.course.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public AiServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${application.ai-service.url:http://localhost:8000}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        this.restClient = restClientBuilder
                .baseUrl(this.baseUrl)
                .build();
    }

    /**
     * Sends extracted document pages to the Python FastAPI microservice for cleaning and chunking.
     *
     * @param request Payload containing document metadata and extracted text per page
     * @return Structured response with validated chunks
     * @throws AiServiceException in case of network timeout, connection refusal or HTTP error
     */
    public AiChunkingResponse processChunking(AiChunkingRequest request) {
        try {
            log.info("Appel du service IA Python sur {}/api/v1/chunking/process pour le document #{}", this.baseUrl, request.getDocumentId());
            AiChunkingResponse response = this.restClient.post()
                    .uri("/api/v1/chunking/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiChunkingResponse.class);

            if (response == null) {
                throw new AiServiceException("Le service IA a retourné une réponse vide (null).");
            }

            return response;
        } catch (ResourceAccessException e) {
            log.error("Délai dépassé ou service IA indisponible à l'adresse {} : {}", this.baseUrl, e.getMessage());
            throw new AiServiceException("Le service IA Python est actuellement indisponible ou ne répond pas (" + this.baseUrl + ").", e);
        } catch (RestClientResponseException e) {
            log.error("Le service IA a retourné une erreur HTTP {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServiceException("Le service IA a rejeté la requête : HTTP " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString(), e);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la communication avec le service IA : {}", e.getMessage(), e);
            throw new AiServiceException("Erreur inattendue lors de la communication avec le service IA : " + e.getMessage(), e);
        }
    }

    /**
     * Checks health and reachability of the FastAPI AI service.
     */
    public boolean isHealthy() {
        try {
            String resp = this.restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(String.class);
            return resp != null && resp.contains("UP");
        } catch (Exception e) {
            return false;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
