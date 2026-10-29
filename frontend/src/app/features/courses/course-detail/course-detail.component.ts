import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CourseService } from '../../../core/course/course.service';
import { Course, CourseDocument, ProcessingStatus } from '../../../core/course/course.models';

@Component({
  selector: 'app-course-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './course-detail.component.html',
  styleUrls: ['./course-detail.component.css']
})
export class CourseDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private courseService = inject(CourseService);

  // Core state signals
  courseId = signal<number | null>(null);
  course = signal<Course | null>(null);
  documents = signal<CourseDocument[]>([]);

  // Loading & Error states
  isLoading = signal(true);
  isLoadingDocuments = signal(false);
  isUploading = signal(false);
  isDeleting = signal(false);
  courseNotFound = signal(false);
  errorMessage = signal<string | null>(null);
  uploadErrorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Text viewer state
  viewingDocument = signal<CourseDocument | null>(null);
  isLoadingText = signal(false);
  copiedText = signal(false);

  // Document deletion modal state
  deletingDocument = signal<CourseDocument | null>(null);

  // Upload area toggle
  showUploadArea = signal(false);

  // Computed properties
  hasDocuments = computed(() => this.documents().length > 0);
  documentsCount = computed(() => this.documents().length);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const idParam = params.get('id');
      const id = idParam ? parseInt(idParam, 10) : null;

      if (!id || isNaN(id)) {
        this.courseNotFound.set(true);
        this.errorMessage.set('Identifiant de cours invalide.');
        this.isLoading.set(false);
        return;
      }

      this.courseId.set(id);
      this.loadCourseAndDocuments(id);
    });
  }

  loadCourseAndDocuments(id: number): void {
    this.isLoading.set(true);
    this.courseNotFound.set(false);
    this.errorMessage.set(null);

    this.courseService.getCourseById(id).subscribe({
      next: (courseData) => {
        this.course.set(courseData);
        this.isLoading.set(false);
        this.loadDocuments(id);
      },
      error: () => {
        this.courseNotFound.set(true);
        this.errorMessage.set("Ce cours est introuvable ou vous n'avez pas l'autorisation d'y accéder.");
        this.isLoading.set(false);
      }
    });
  }

  loadDocuments(courseId: number): void {
    this.isLoadingDocuments.set(true);
    this.courseService.getCourseDocuments(courseId).subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.isLoadingDocuments.set(false);
      },
      error: () => {
        this.isLoadingDocuments.set(false);
      }
    });
  }

  // --- File Upload ---

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    this.uploadErrorMessage.set(null);

    // Client-side validation
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.uploadErrorMessage.set('Seuls les fichiers PDF sont acceptés (.pdf).');
      input.value = '';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.uploadErrorMessage.set('Le fichier dépasse la taille maximale autorisée de 10 Mo.');
      input.value = '';
      return;
    }

    const currentCourseId = this.courseId();
    if (!currentCourseId) return;

    this.isUploading.set(true);

    this.courseService.uploadCourseDocument(currentCourseId, file).subscribe({
      next: (createdDoc) => {
        this.documents.update((docs) => [createdDoc, ...docs]);
        this.isUploading.set(false);
        this.showUploadArea.set(false);
        input.value = '';
        this.showSuccess(`Document "${createdDoc.originalFilename}" ajouté avec succès !`);
      },
      error: (err) => {
        this.isUploading.set(false);
        input.value = '';
        this.uploadErrorMessage.set(
          err.error?.message || 'Erreur lors du téléversement du document PDF.'
        );
      }
    });
  }

  // --- Text Extraction Viewer ---

  openTextViewer(doc: CourseDocument): void {
    this.viewingDocument.set(doc);
    this.copiedText.set(false);

    // If extracted text is not loaded yet, fetch full document details
    if (doc.extractedText === undefined || doc.extractedText === null) {
      const currentCourseId = this.courseId();
      if (!currentCourseId) return;

      this.isLoadingText.set(true);
      this.courseService.getCourseDocument(currentCourseId, doc.id).subscribe({
        next: (fullDoc) => {
          this.viewingDocument.set(fullDoc);
          this.isLoadingText.set(false);
          // Update in array cache
          this.documents.update((list) =>
            list.map((d) => (d.id === doc.id ? { ...d, extractedText: fullDoc.extractedText } : d))
          );
        },
        error: () => {
          this.isLoadingText.set(false);
        }
      });
    }
  }

  closeTextViewer(): void {
    this.viewingDocument.set(null);
    this.copiedText.set(false);
  }

  copyExtractedText(): void {
    const text = this.viewingDocument()?.extractedText;
    if (!text) return;

    navigator.clipboard.writeText(text).then(() => {
      this.copiedText.set(true);
      setTimeout(() => {
        this.copiedText.set(false);
      }, 2500);
    });
  }

  // --- Document Deletion ---

  confirmDeleteDocument(doc: CourseDocument): void {
    this.deletingDocument.set(doc);
  }

  cancelDeleteDocument(): void {
    this.deletingDocument.set(null);
  }

  onDeleteDocument(): void {
    const docToDelete = this.deletingDocument();
    const currentCourseId = this.courseId();
    if (!docToDelete || !currentCourseId) return;

    this.isDeleting.set(true);

    this.courseService.deleteCourseDocument(currentCourseId, docToDelete.id).subscribe({
      next: () => {
        this.documents.update((docs) => docs.filter((d) => d.id !== docToDelete.id));
        this.isDeleting.set(false);
        this.cancelDeleteDocument();
        this.showSuccess(`Document "${docToDelete.originalFilename}" supprimé avec succès.`);
      },
      error: () => {
        this.isDeleting.set(false);
        this.showError('Erreur lors de la suppression du document.');
      }
    });
  }

  // --- Status & Helpers ---

  getStatusLabel(doc: CourseDocument): string {
    const status = doc.processingStatus;
    switch (status) {
      case 'COMPLETED':
        return 'Traité';
      case 'NO_TEXT':
        return 'Aucun texte (scanné)';
      case 'PROCESSING':
        return 'Traitement en cours';
      case 'PENDING':
        return 'En attente';
      case 'FAILED':
        return 'Échec du traitement';
      default:
        return 'En attente';
    }
  }

  getStatusBadgeClass(status?: ProcessingStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-badge-completed';
      case 'NO_TEXT':
        return 'status-badge-warning';
      case 'PROCESSING':
        return 'status-badge-processing';
      case 'PENDING':
        return 'status-badge-pending';
      case 'FAILED':
        return 'status-badge-failed';
      default:
        return 'status-badge-pending';
    }
  }

  formatFileSize(bytes?: number | null): string {
    if (!bytes && bytes !== 0) return '0 Ko';
    if (bytes < 1024) return `${bytes} octets`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} Mo`;
  }

  formatDate(dateStr?: string | null): string {
    if (!dateStr) return 'Non renseignée';
    try {
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return dateStr;
      return new Intl.DateTimeFormat('fr-FR', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      }).format(date);
    } catch {
      return dateStr;
    }
  }

  private showSuccess(msg: string): void {
    this.successMessage.set(msg);
    setTimeout(() => {
      this.successMessage.set(null);
    }, 4500);
  }

  private showError(msg: string): void {
    this.errorMessage.set(msg);
    setTimeout(() => {
      this.errorMessage.set(null);
    }, 5000);
  }
}
