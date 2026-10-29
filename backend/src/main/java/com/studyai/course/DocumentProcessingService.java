package com.studyai.course;

import com.studyai.course.client.AiChunkItem;
import com.studyai.course.client.AiChunkingRequest;
import com.studyai.course.client.AiChunkingResponse;
import com.studyai.course.client.AiPageInput;
import com.studyai.course.client.AiServiceClient;
import com.studyai.course.client.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentService documentService;
    private final AiServiceClient aiServiceClient;
    private final CourseDocumentRepository courseDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public DocumentProcessingService(DocumentService documentService,
                                     AiServiceClient aiServiceClient,
                                     CourseDocumentRepository courseDocumentRepository,
                                     DocumentChunkRepository documentChunkRepository) {
        this.documentService = documentService;
        this.aiServiceClient = aiServiceClient;
        this.courseDocumentRepository = courseDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    /**
     * Processes a course document through the hybrid RAG pipeline:
     * 1. Extracts text page-by-page in Spring Boot using PDFBox.
     * 2. Delegates text cleaning and linguistic chunking to the independent Python FastAPI AI service.
     * 3. Validates the response and atomically persists the chunks in MySQL (only on success).
     * 4. Preserves previous chunks intact if the Python AI service fails or times out.
     *
     * @param document CourseDocument to process
     * @return Updated CourseDocument
     */
    public CourseDocument processDocument(CourseDocument document) {
        if (document == null) {
            return null;
        }

        log.info("Début du traitement RAG hybride pour le document #{} ({})", document.getId(), document.getOriginalFilename());
        document.setProcessingStatus(ProcessingStatus.PROCESSING);
        document.setProcessingError(null);

        try {
            // 1. Extract pages using PDFBox in Spring Boot
            List<PageText> pages = new ArrayList<>();
            String storagePathStr = document.getStoragePath();
            Path storagePath = storagePathStr != null ? Paths.get(storagePathStr) : null;

            if (storagePath != null && Files.exists(storagePath)) {
                try {
                    pages = documentService.extractPages(storagePath);
                } catch (Exception e) {
                    log.warn("Extraction par page impossible depuis le fichier physique : {}", e.getMessage());
                }
            }

            // Fallback to existing extracted text if pages list is empty
            if (pages.isEmpty()) {
                String existingText = document.getExtractedText();
                if (existingText != null && !existingText.isBlank()) {
                    pages.add(new PageText(1, existingText));
                }
            }

            // Update full extracted text if not present
            StringBuilder fullTextBuilder = new StringBuilder();
            for (PageText page : pages) {
                if (page.text() != null && !page.text().isBlank()) {
                    fullTextBuilder.append(page.text()).append("\n\n");
                }
            }
            String combinedRawText = fullTextBuilder.toString().trim();
            if (!combinedRawText.isEmpty() && (document.getExtractedText() == null || document.getExtractedText().isBlank())) {
                document.setExtractedText(combinedRawText);
            }

            // 2. Build request for Python FastAPI AI service
            List<AiPageInput> aiPages = new ArrayList<>();
            for (PageText pt : pages) {
                aiPages.add(new AiPageInput(pt.pageNumber(), pt.text()));
            }

            AiChunkingRequest aiRequest = new AiChunkingRequest(
                    document.getId(),
                    aiPages,
                    document.getExtractedText(),
                    800, // Target chunk size
                    120  // Chunk overlap
            );

            // 3. Call Python FastAPI AI service for cleaning & chunking
            AiChunkingResponse aiResponse = aiServiceClient.processChunking(aiRequest);

            if (aiResponse == null) {
                throw new AiServiceException("Le service IA Python a renvoyé une réponse nulle.");
            }

            // 4. Handle FAILED status from Python
            if ("FAILED".equalsIgnoreCase(aiResponse.getStatus())) {
                log.warn("Le service IA a retourné un statut FAILED pour le document #{} : {}", document.getId(), aiResponse.getErrorMessage());
                document.setProcessingStatus(ProcessingStatus.FAILED);
                document.setProcessingError(aiResponse.getErrorMessage() != null ? aiResponse.getErrorMessage() : "Erreur signalée par le service IA.");
                return courseDocumentRepository.save(document);
            }

            // 5. Handle NO_TEXT response (scanned / image-only / empty PDF)
            if ("NO_TEXT".equalsIgnoreCase(aiResponse.getStatus()) || !aiResponse.isExploitable()) {
                log.info("Document #{} classifié NO_TEXT par le service IA (aucun texte exploitable).", document.getId());
                documentChunkRepository.deleteByDocument(document);
                document.clearChunks();
                document.setProcessingStatus(ProcessingStatus.NO_TEXT);
                document.setChunksCount(0);
                document.setProcessingError(null);
                return courseDocumentRepository.save(document);
            }

            // 5. Handle COMPLETED response
            if ("COMPLETED".equalsIgnoreCase(aiResponse.getStatus())) {
                List<AiChunkItem> returnedChunks = aiResponse.getChunks();
                if (returnedChunks == null || returnedChunks.isEmpty()) {
                    log.info("Document #{} : Statut COMPLETED mais aucun chunk généré.", document.getId());
                    documentChunkRepository.deleteByDocument(document);
                    document.clearChunks();
                    document.setProcessingStatus(ProcessingStatus.NO_TEXT);
                    document.setChunksCount(0);
                    document.setProcessingError(null);
                    return courseDocumentRepository.save(document);
                }

                // Atomically purge old chunks and save new ones
                documentChunkRepository.deleteByDocument(document);
                document.clearChunks();

                List<DocumentChunk> newChunks = new ArrayList<>();
                for (AiChunkItem item : returnedChunks) {
                    DocumentChunk chunk = new DocumentChunk(
                            document,
                            item.getChunkIndex(),
                            item.getContent(),
                            item.getPageNumber()
                    );
                    newChunks.add(chunk);
                }

                documentChunkRepository.saveAll(newChunks);
                document.setProcessingStatus(ProcessingStatus.COMPLETED);
                document.setChunksCount(newChunks.size());
                document.setProcessingError(null);
                log.info("Traitement RAG réussi via Python pour le document #{}: {} chunks persistés dans MySQL.", document.getId(), newChunks.size());
                return courseDocumentRepository.save(document);
            }

            // 6. Handle FAILED status from Python
            log.warn("Le service IA a retourné un statut FAILED pour le document #{} : {}", document.getId(), aiResponse.getErrorMessage());
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(aiResponse.getErrorMessage() != null ? aiResponse.getErrorMessage() : "Erreur signalée par le service IA.");
            return courseDocumentRepository.save(document);

        } catch (AiServiceException e) {
            log.error("Échec de l'appel au service IA Python pour le document #{}: {}", document.getId(), e.getMessage());
            // Preserve existing chunks! Do not clear them if communication failed!
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(e.getMessage());
            return courseDocumentRepository.save(document);
        } catch (Exception e) {
            log.error("Erreur inattendue lors du traitement RAG du document #{}: {}", document.getId(), e.getMessage(), e);
            document.setProcessingStatus(ProcessingStatus.FAILED);
            document.setProcessingError(e.getMessage() != null ? e.getMessage() : "Erreur inattendue");
            return courseDocumentRepository.save(document);
        }
    }
}
