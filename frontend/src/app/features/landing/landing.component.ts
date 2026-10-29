import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CourseService } from '../../core/course/course.service';
import { User } from '../../core/auth/auth.models';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.css']
})
export class LandingComponent implements OnInit {
  authService = inject(AuthService);
  courseService = inject(CourseService);

  user = signal<User | null>(null);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);

  // Computes user initials (e.g. "Karim Ben Ali" -> "KB", "Sarah" -> "S")
  userInitials = computed(() => {
    const u = this.user();
    if (!u || !u.fullName) return 'ET';
    const parts = u.fullName.trim().split(/\s+/);
    if (parts.length === 1) {
      return parts[0].substring(0, Math.min(2, parts[0].length)).toUpperCase();
    }
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  });

  // Extracts student first name for personalized greeting
  userFirstName = computed(() => {
    const u = this.user();
    if (!u || !u.fullName) return 'Étudiant';
    return u.fullName.trim().split(/\s+/)[0];
  });

  // Human-readable formatted registration date
  formattedCreatedAt = computed(() => {
    const u = this.user();
    if (!u || !u.createdAt) return 'Non renseignée';
    try {
      const date = new Date(u.createdAt);
      if (isNaN(date.getTime())) return u.createdAt;
      return new Intl.DateTimeFormat('fr-FR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }).format(date);
    } catch {
      return u.createdAt;
    }
  });

  // Real course count from CourseService
  courseCount = computed(() => this.courseService.courses().length);
  recentCourses = computed(() => this.courseService.courses().slice(0, 3));

  ngOnInit(): void {
    // Immediately show existing cached user if available
    const cachedUser = this.authService.currentUser();
    if (cachedUser) {
      this.user.set(cachedUser);
      this.isLoading.set(false);
    }

    // Refresh from backend to ensure synchronization with real MySQL data
    this.loadProfile();
    this.loadCourses();
  }

  loadProfile(): void {
    if (!this.user()) {
      this.isLoading.set(true);
    }
    this.errorMessage.set(null);

    this.authService.getCurrentUser().subscribe({
      next: (profile) => {
        this.user.set(profile);
        this.isLoading.set(false);
      },
      error: () => {
        if (!this.user()) {
          this.errorMessage.set('Impossible de charger votre profil sécurisé.');
        }
        this.isLoading.set(false);
      }
    });
  }

  loadCourses(): void {
    this.courseService.getCourses().subscribe({
      error: () => {
        // Silently handle course loading error on dashboard
      }
    });
  }

  formatCourseDate(dateStr?: string | null): string {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return dateStr;
      return new Intl.DateTimeFormat('fr-FR', {
        day: 'numeric',
        month: 'short'
      }).format(date);
    } catch {
      return dateStr;
    }
  }

  onLogout(): void {
    this.authService.logout();
  }
}

