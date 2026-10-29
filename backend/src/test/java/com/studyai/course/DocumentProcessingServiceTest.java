package com.studyai.course;

import com.studyai.course.client.AiChunkItem;
import com.studyai.course.client.AiChunkingRequest;
import com.studyai.course.client.AiChunkingResponse;
import com.studyai.course.client.AiServiceClient;
import com.studyai.course.client.AiServiceException;
import com.studyai.user.Role;
import com.studyai.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private CourseDocumentRepository courseDocumentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private User student;
    private Course course;
    private CourseDocument document;

    @BeforeEach
    void setUp() {
        student = new User("Alice Dupont", "alice@studyai.test", "hashPassword", Role.STUDENT);
        course = new Course("RAG Architecture", "Deep learning & IR", student);
        document = new CourseDocument(course, "rag_guide.pdf", "uploads/test.pdf", 1024L, "application/pdf", "Texte extrait complet du document");
        document.setId(10L);
    }

    @Test
    @DisplayName("Should successfully process document and save chunks returned by Python AI service")
    void shouldProcessDocumentSuccessfullyViaAiService() {
        List<AiChunkItem> returnedChunks = List.of(
                new AiChunkItem(0, "Premier chunk généré par Python", 1, 31),
                new AiChunkItem(1, "Deuxième chunk généré par Python", 2, 32)
        );
        AiChunkingResponse aiResponse = new AiChunkingResponse("COMPLETED", true, 2, returnedChunks, null);

        when(aiServiceClient.processChunking(any(AiChunkingRequest.class))).thenReturn(aiResponse);
        when(courseDocumentRepository.save(any(CourseDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDocument result = documentProcessingService.processDocument(document);

        assertThat(result).isNotNull();
        assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(result.getChunksCount()).isEqualTo(2);
        assertThat(result.getProcessingError()).isNull();

        verify(documentChunkRepository).deleteByDocument(document);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkRepository).saveAll(chunksCaptor.capture());

        List<DocumentChunk> savedChunks = chunksCaptor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(savedChunks.get(0).getContent()).isEqualTo("Premier chunk généré par Python");
        assertThat(savedChunks.get(0).getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should set status NO_TEXT and zero chunks when Python AI service identifies scanned PDF")
    void shouldHandleNoTextResponseFromAiService() {
        AiChunkingResponse aiResponse = new AiChunkingResponse("NO_TEXT", false, 0, new ArrayList<>(), null);

        when(aiServiceClient.processChunking(any(AiChunkingRequest.class))).thenReturn(aiResponse);
        when(courseDocumentRepository.save(any(CourseDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDocument result = documentProcessingService.processDocument(document);

        assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.NO_TEXT);
        assertThat(result.getChunksCount()).isEqualTo(0);
        assertThat(result.getProcessingError()).isNull();
        verify(documentChunkRepository).deleteByDocument(document);
        verify(documentChunkRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should PRESERVE existing chunks when Python AI service fails with connection or timeout error")
    void shouldPreserveExistingChunksWhenAiServiceFails() {
        // Document initially had 5 chunks from a previous successful run
        document.setChunksCount(5);
        document.setProcessingStatus(ProcessingStatus.COMPLETED);

        when(aiServiceClient.processChunking(any(AiChunkingRequest.class)))
                .thenThrow(new AiServiceException("Le service IA Python est actuellement indisponible (timeout)."));
        when(courseDocumentRepository.save(any(CourseDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDocument result = documentProcessingService.processDocument(document);

        assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(result.getProcessingError()).contains("timeout");
        // Crucial requirement: previous chunks were NOT deleted!
        verify(documentChunkRepository, never()).deleteByDocument(document);
        verify(documentChunkRepository, never()).saveAll(any());
        assertThat(result.getChunksCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should handle unexpected error from Python service by setting FAILED status")
    void shouldHandleFailedStatusFromPythonService() {
        AiChunkingResponse aiResponse = new AiChunkingResponse("FAILED", false, 0, new ArrayList<>(), "Mémoire insuffisante pour segmentation");

        when(aiServiceClient.processChunking(any(AiChunkingRequest.class))).thenReturn(aiResponse);
        when(courseDocumentRepository.save(any(CourseDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseDocument result = documentProcessingService.processDocument(document);

        assertThat(result.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(result.getProcessingError()).isEqualTo("Mémoire insuffisante pour segmentation");
        verify(documentChunkRepository, never()).deleteByDocument(document);
    }
}
