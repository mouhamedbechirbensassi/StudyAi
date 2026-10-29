package com.studyai.course;

import com.studyai.course.dto.CourseDocumentResponse;
import com.studyai.course.dto.CourseRequest;
import com.studyai.course.dto.CourseResponse;
import com.studyai.course.dto.DocumentChunkResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(
            @Valid @RequestBody CourseRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseResponse response = courseService.createCourse(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getCourses(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<CourseResponse> response = courseService.getCourses(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getCourseById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseResponse response = courseService.getCourseById(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseResponse response = courseService.updateCourse(id, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        courseService.deleteCourse(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    // --- Multi-Document Collection Endpoints ---

    @PostMapping(value = "/{courseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDocumentResponse> addCourseDocument(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseDocumentResponse response = courseService.addDocument(courseId, file, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{courseId}/documents")
    public ResponseEntity<List<CourseDocumentResponse>> getCourseDocuments(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<CourseDocumentResponse> response = courseService.getCourseDocuments(courseId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{courseId}/documents/{documentId}")
    public ResponseEntity<CourseDocumentResponse> getCourseDocumentById(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseDocumentResponse response = courseService.getCourseDocument(courseId, documentId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{courseId}/documents/{documentId}")
    public ResponseEntity<Void> deleteCourseDocument(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        courseService.deleteCourseDocument(courseId, documentId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/documents/{documentId}/process")
    public ResponseEntity<CourseDocumentResponse> reprocessCourseDocument(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseDocumentResponse response = courseService.reprocessDocument(courseId, documentId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{courseId}/documents/{documentId}/chunks")
    public ResponseEntity<List<DocumentChunkResponse>> getCourseDocumentChunks(
            @PathVariable Long courseId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DocumentChunkResponse> response = courseService.getDocumentChunks(courseId, documentId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // --- Legacy Document Endpoints (Backward Compatibility) ---

    @PostMapping(value = "/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDocumentResponse> uploadDocumentLegacy(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseDocumentResponse response = courseService.attachDocument(id, file, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<CourseDocumentResponse> getDocumentLegacy(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        CourseDocumentResponse response = courseService.getCourseDocument(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/document")
    public ResponseEntity<Void> deleteDocumentLegacy(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        courseService.deleteCourseDocument(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
