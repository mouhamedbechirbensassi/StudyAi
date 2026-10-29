import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { CourseService } from '../../core/course/course.service';
import { Course, CourseRequest } from '../../core/course/course.models';
import { AuthService } from '../../core/auth/auth.service';

type ModalMode = 'none' | 'create' | 'edit' | 'delete';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './courses.component.html',
  styleUrls: ['./courses.component.css']
})
export class CoursesComponent implements OnInit {
  private router = inject(Router);
  courseService = inject(CourseService);
  authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  // Component state signals
  courses = signal<Course[]>([]);
  isLoading = signal(true);
  isSubmitting = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Modal & Selection state
  modalMode = signal<ModalMode>('none');
  selectedCourse = signal<Course | null>(null);

  // Form state
  formTitle = signal('');
  formDescription = signal('');
  titleTouched = signal(false);

  // Computed properties
  user = this.authService.currentUser;
  hasCourses = computed(() => this.courses().length > 0);
  courseCount = computed(() => this.courses().length);

  isTitleValid = computed(() => {
    const title = this.formTitle().trim();
    return title.length >= 1 && title.length <= 150;
  });

  titleErrorMessage = computed(() => {
    if (!this.titleTouched()) return '';
    const title = this.formTitle().trim();
    if (title.length === 0) return 'Le titre du cours est obligatoire.';
    if (title.length > 150) return 'Le titre ne peut pas dépasser 150 caractères.';
    return '';
  });

  ngOnInit(): void {
    this.loadCourses();

    // Check if query param ?create=true was passed
    this.route.queryParams.subscribe((params) => {
      if (params['create'] === 'true') {
        this.openCreateModal();
      }
    });
  }

  loadCourses(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.courseService.getCourses().subscribe({
      next: (data) => {
        this.courses.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger vos cours. Veuillez réessayer.');
        this.isLoading.set(false);
      }
    });
  }

  // --- Navigation to Dedicated Course Page ---

  goToCourse(courseId: number): void {
    this.router.navigate(['/courses', courseId]);
  }

  // --- Modal Triggers ---

  openCreateModal(): void {
    this.resetForm();
    this.modalMode.set('create');
  }

  openEditModal(course: Course, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.resetForm();
    this.selectedCourse.set(course);
    this.formTitle.set(course.title);
    this.formDescription.set(course.description || '');
    this.modalMode.set('edit');
  }

  openDeleteModal(course: Course, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.selectedCourse.set(course);
    this.modalMode.set('delete');
  }

  closeModal(): void {
    this.modalMode.set('none');
    this.selectedCourse.set(null);
    this.resetForm();
  }

  private resetForm(): void {
    this.formTitle.set('');
    this.formDescription.set('');
    this.titleTouched.set(false);
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  // --- Form Actions ---

  onSubmit(): void {
    this.titleTouched.set(true);
    if (!this.isTitleValid()) return;

    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const payload: CourseRequest = {
      title: this.formTitle().trim(),
      description: this.formDescription().trim() || null
    };

    if (this.modalMode() === 'create') {
      this.courseService.createCourse(payload).subscribe({
        next: (created) => {
          this.loadCourses();
          this.isSubmitting.set(false);
          this.closeModal();
          this.showSuccess('Cours créé avec succès !');
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.errorMessage.set(
            err.error?.message || 'Erreur lors de la création du cours.'
          );
        }
      });
    } else if (this.modalMode() === 'edit' && this.selectedCourse()) {
      const courseId = this.selectedCourse()!.id;
      this.courseService.updateCourse(courseId, payload).subscribe({
        next: (updated) => {
          this.courses.update((list) =>
            list.map((c) => (c.id === courseId ? { ...c, ...updated } : c))
          );
          this.isSubmitting.set(false);
          this.closeModal();
          this.showSuccess('Cours mis à jour avec succès !');
        },
        error: (err) => {
          this.isSubmitting.set(false);
          this.errorMessage.set(
            err.error?.message || 'Erreur lors de la modification du cours.'
          );
        }
      });
    }
  }

  onConfirmDelete(): void {
    const course = this.selectedCourse();
    if (!course) return;

    this.isSubmitting.set(true);
    this.courseService.deleteCourse(course.id).subscribe({
      next: () => {
        this.loadCourses();
        this.isSubmitting.set(false);
        this.closeModal();
        this.showSuccess(`Le cours "${course.title}" a été supprimé.`);
      },
      error: () => {
        this.isSubmitting.set(false);
        this.errorMessage.set('Erreur lors de la suppression du cours.');
      }
    });
  }

  private showSuccess(msg: string): void {
    this.successMessage.set(msg);
    setTimeout(() => {
      this.successMessage.set(null);
    }, 4000);
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
}
