package com.studyai.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentOrderByChunkIndexAsc(CourseDocument document);

    long countByDocument(CourseDocument document);

    @org.springframework.data.jpa.repository.Modifying
    void deleteByDocument(CourseDocument document);
}
