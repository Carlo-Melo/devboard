import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BoardResponse } from '../../../core/models/board.models';
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
  private readonly destroyRef = inject(DestroyRef);
  showArchivedBoards = false;
  archivedBoards: BoardResponse[] = [];
  archivedPage = 0;
  archivedPages = 0;
  archivedLoading = false;
  boardBusyId: number | null = null;
  boardError: string | null = null;
  boardMessage: string | null = null;

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
    return !!this.project?.currentUserOwner || this.project?.currentUserRole === 'ADMIN';
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
    this.projectService.getById(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.boards.create(this.project.id, this.boardForm.getRawValue()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.projectService.archive(this.project.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.router.navigateByUrl('/projects/list'),
      error: (err: ApiError) => {
        this.isArchiving = false;
        this.errorMessage = err.message;
      }
    });
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }

  canArchiveBoard(board: { name: string; defaultBoard: boolean }): boolean {
    return this.canEdit && !board.defaultBoard && board.name.trim().toLowerCase() !== 'main board';
  }

  archiveBoard(board: { id: number; name: string; defaultBoard: boolean }): void {
    if (!this.project || !this.canArchiveBoard(board) || this.boardBusyId !== null) return;
    if (!confirm(`Arquivar o board "${board.name}"? As tarefas e atividades serão preservadas. O repositório ficará disponível para outro board ativo.`)) return;
    this.boardBusyId = board.id; this.boardError = null; this.boardMessage = null;
    this.boards.archive(board.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.project!.boards = this.project!.boards.filter(item => item.id !== board.id);
        this.boardBusyId = null; this.boardMessage = `Board "${board.name}" arquivado. Suas tarefas e atividades foram preservadas.`;
        if (this.showArchivedBoards) this.loadArchivedBoards(0);
      },
      error: (error: ApiError) => { this.boardBusyId = null; this.boardError = error.message; }
    });
  }

  toggleArchivedBoards(): void {
    if (!this.canEdit) return;
    this.showArchivedBoards = !this.showArchivedBoards;
    if (this.showArchivedBoards) this.loadArchivedBoards(0);
  }

  loadArchivedBoards(page: number): void {
    if (!this.project || !this.canEdit || this.archivedLoading || page < 0) return;
    this.archivedLoading = true; this.boardError = null;
    this.boards.listArchived(this.project.id, page).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.archivedBoards = result.content; this.archivedPage = result.page; this.archivedPages = result.totalPages; this.archivedLoading = false; },
      error: (error: ApiError) => { this.archivedLoading = false; this.boardError = error.message; }
    });
  }

  restoreBoard(board: BoardResponse): void {
    if (!this.project || !this.canEdit || this.boardBusyId !== null) return;
    this.boardBusyId = board.id; this.boardError = null; this.boardMessage = null;
    this.boards.restore(board.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: restored => {
        this.project!.boards.push({ id: restored.id, name: restored.name, defaultBoard: restored.defaultBoard, githubLinked: !!restored.githubRepoId });
        this.boardBusyId = null; this.boardMessage = `Board "${board.name}" restaurado.`;
        this.loadArchivedBoards(0);
      },
      error: (error: ApiError) => { this.boardBusyId = null; this.boardError = error.message; }
    });
  }
}
