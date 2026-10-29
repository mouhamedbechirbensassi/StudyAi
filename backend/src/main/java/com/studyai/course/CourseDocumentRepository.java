package com.studyai.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseDocumentRepository extends JpaRepository<CourseDocument, Long> {

    List<CourseDocument> findByCourseOrderByUploadedAtDesc(Course course);

    Optional<CourseDocument> findByIdAndCourse(Long id, Course course);

    boolean existsByCourseAndStoragePath(Course course, String storagePath);

    long countByCourse(Course course);

    void deleteByCourse(Course course);
}
