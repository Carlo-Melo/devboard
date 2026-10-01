import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BoardService } from '../../../core/services/board.service';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectResponse } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss'
})
export class ProjectDetailComponent implements OnInit {

  readonly boardForm = this.fb.nonNullable.group({ name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]], description: [''] });
  creatingBoard = false;
  project: ProjectResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  isArchiving = false;

  constructor(
    private projectService: ProjectService,
    private boards: BoardService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  get canEdit(): boolean {
    return this.project?.currentUserRole === 'ADMIN';
  }

  get isOwner(): boolean {
    return !!this.project?.currentUserOwner;
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  private load(id: number): void {
    this.isLoading = true;
    this.projectService.getById(id).subscribe({
      next: (project) => {
        this.project = project;
        this.isLoading = false;
      },
      error: (err: ApiError) => {
        this.errorMessage = err.status === 404
          ? 'Projeto não encontrado.'
          : 'Não foi possível carregar o projeto.';
        this.isLoading = false;
      }
    });
  }

  createBoard(): void {
    if (!this.project || this.boardForm.invalid || this.creatingBoard) { this.boardForm.markAllAsTouched(); return; }
    this.creatingBoard = true;
    this.boards.create(this.project.id, this.boardForm.getRawValue()).subscribe({
      next: board => { this.creatingBoard = false; this.router.navigate(['/boards', board.id]); },
      error: (error: ApiError) => { this.creatingBoard = false; this.errorMessage = error.message; }
    });
  }

  archive(): void {
    if (!this.project) {
      return;
    }
    if (!confirm(`Arquivar o projeto "${this.project.name}"? Ele deixará de aparecer na listagem.`)) {
      return;
    }

    this.isArchiving = true;
    this.projectService.archive(this.project.id).subscribe({
      next: () => this.router.navigateByUrl('/projects'),
      error: (err: ApiError) => {
        this.isArchiving = false;
        this.errorMessage = err.message;
      }
    });
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }
}
