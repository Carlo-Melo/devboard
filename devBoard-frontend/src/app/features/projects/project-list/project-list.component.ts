import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, of, switchMap } from 'rxjs';
import { ProjectSummaryResponse } from '../../../core/models/project.models';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectCardComponent } from '../project-card/project-card.component';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ProjectCardComponent],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss'
})
export class ProjectListComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly pageRequest$ = new BehaviorSubject(0);
  readonly pageSize = 12;
  projects: ProjectSummaryResponse[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  isLoading = true;
  errorMessage: string | null = null;

  constructor(private projectService: ProjectService) {}

  ngOnInit(): void {
    this.pageRequest$.pipe(
      switchMap(page => {
        this.isLoading = true;
        this.errorMessage = null;
        return this.projectService.list(false, page, this.pageSize).pipe(
          catchError(() => {
            this.errorMessage = 'Não foi possível carregar os projetos.';
            return of(null);
          })
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      if (result) {
        this.projects = result.content;
        this.page = result.page;
        this.totalPages = result.totalPages;
        this.totalElements = result.totalElements;
      }
      this.isLoading = false;
    });
  }

  loadProjects(): void { this.pageRequest$.next(this.page); }
  nextPage(): void {
    if (!this.isLoading && this.page + 1 < this.totalPages) this.pageRequest$.next(this.page + 1);
  }
  previousPage(): void {
    if (!this.isLoading && this.page > 0) this.pageRequest$.next(this.page - 1);
  }
}