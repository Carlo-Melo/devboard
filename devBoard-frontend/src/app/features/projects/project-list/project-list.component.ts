import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { AuthService } from '../../../core/services/auth.service';
import { ProjectSummaryResponse } from '../../../core/models/project.models';
import { UserResponse } from '../../../core/models/auth.models';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss'
})
export class ProjectListComponent implements OnInit {

  projects: ProjectSummaryResponse[] = [];
  currentUser: UserResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  page = 0;
  totalPages = 0;
  readonly pageSize = 12;

  constructor(
    private projectService: ProjectService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadProjects();
    this.authService.getMe().subscribe({
      next: (user) => this.currentUser = user,
      error: () => {}
    });
  }

  loadProjects(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.projectService.list(false, this.page, this.pageSize).subscribe({
      next: (response) => {
        this.projects = response.content;
        this.totalPages = response.totalPages;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Não foi possível carregar os projetos.';
        this.isLoading = false;
      }
    });
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.loadProjects();
    }
  }

  previousPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadProjects();
    }
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login')
    });
  }
}
