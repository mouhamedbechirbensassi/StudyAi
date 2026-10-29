import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Course, CourseRequest, CourseDocument, DocumentChunk } from './course.models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CourseService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/courses`;

  // Reactive state for courses
  courses = signal<Course[]>([]);
  isLoading = signal(false);

  getCourses(): Observable<Course[]> {
    this.isLoading.set(true);
    return this.http.get<Course[]>(this.apiUrl).pipe(
      tap({
        next: (courses) => {
          this.courses.set(courses);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        }
      })
    );
  }

  getCourseById(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.apiUrl}/${id}`);
  }

  createCourse(request: CourseRequest): Observable<Course> {
    return this.http.post<Course>(this.apiUrl, request).pipe(
      tap((newCourse) => {
        this.getCourses().subscribe();
      })
    );
  }

  updateCourse(id: number, request: CourseRequest): Observable<Course> {
    return this.http.put<Course>(`${this.apiUrl}/${id}`, request).pipe(
      tap((updated) => {
        this.courses.update((list) =>
          list.map((c) => (c.id === id ? { ...c, ...updated } : c))
        );
      })
    );
  }

  deleteCourse(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        this.courses.update((list) => list.filter((c) => c.id !== id));
        this.getCourses().subscribe();
      })
    );
  }

  // --- Multi-Document Management Methods ---

  getCourseDocuments(courseId: number): Observable<CourseDocument[]> {
    return this.http.get<CourseDocument[]>(`${this.apiUrl}/${courseId}/documents`);
  }

  getCourseDocument(courseId: number, documentId: number): Observable<CourseDocument> {
    return this.http.get<CourseDocument>(`${this.apiUrl}/${courseId}/documents/${documentId}`);
  }

  uploadCourseDocument(courseId: number, file: File): Observable<CourseDocument> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CourseDocument>(`${this.apiUrl}/${courseId}/documents`, formData).pipe(
      tap((doc) => {
        this.courses.update((list) =>
          list.map((c) => {
            if (c.id === courseId) {
              const currentCount = c.documentsCount ?? 0;
              return {
                ...c,
                documentsCount: currentCount + 1,
                hasDocument: true,
                documentFilename: doc.originalFilename,
                documentFileSize: doc.fileSize,
                documentUploadedAt: doc.uploadedAt
              };
            }
            return c;
          })
        );
      })
    );
  }

  deleteCourseDocument(courseId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${courseId}/documents/${documentId}`).pipe(
      tap(() => {
        this.courses.update((list) =>
          list.map((c) => {
            if (c.id === courseId) {
              const newCount = Math.max(0, (c.documentsCount ?? 1) - 1);
              return {
                ...c,
                documentsCount: newCount,
                hasDocument: newCount > 0
              };
            }
            return c;
          })
        );
      })
    );
  }

  reprocessCourseDocument(courseId: number, documentId: number): Observable<CourseDocument> {
    return this.http.post<CourseDocument>(`${this.apiUrl}/${courseId}/documents/${documentId}/process`, {});
  }

  getDocumentChunks(courseId: number, documentId: number): Observable<DocumentChunk[]> {
    return this.http.get<DocumentChunk[]>(`${this.apiUrl}/${courseId}/documents/${documentId}/chunks`);
  }

  // --- Legacy Compatibility Methods ---

  uploadDocument(courseId: number, file: File): Observable<CourseDocument> {
    return this.uploadCourseDocument(courseId, file);
  }

  getDocument(courseId: number): Observable<CourseDocument> {
    return this.http.get<CourseDocument>(`${this.apiUrl}/${courseId}/document`);
  }

  deleteDocument(courseId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${courseId}/document`);
  }
}
