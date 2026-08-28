import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TaskService } from '../../../core/services/task.service';
import { ProjectService } from '../../../core/services/project.service';
import { AuthService } from '../../../core/services/auth.service';
import { ActivityResponse, CommentResponse, TaskPriority, TaskResponse, TaskType } from '../../../core/models/task.models';
import { ProjectMemberResponse } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';
import { PRIORITY_LABELS, PRIORITY_PILL_CLASSES, TYPE_LABELS } from '../../../shared/constants/task-labels';

interface TaskEditForm {
  title: string;
  description: string;
  type: TaskType;
  priority: TaskPriority;
  assigneeId: number | null;
  collaboratorIds: number[];
  dueDate: string;
  estimate: number | null;
}

@Component({
  selector: 'app-task-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './task-detail.component.html',
  styleUrl: './task-detail.component.scss'
})
export class TaskDetailComponent implements OnInit {

  task: TaskResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  projectMembers: ProjectMemberResponse[] = [];
  currentUserId: number | null = null;

  isEditing = false;
  isSaving = false;
  editForm: TaskEditForm = this.emptyForm();

  newCommentContent = '';
  isSubmittingComment = false;

  editingCommentId: number | null = null;
  editingCommentContent = '';

  isArchiving = false;

  private taskId!: number;

  constructor(
    private taskService: TaskService,
    private projectService: ProjectService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.taskId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();

    this.authService.getMe().subscribe({
      next: (user) => (this.currentUserId = user.id),
      error: () => undefined
    });
  }

  load(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.taskService.getById(this.taskId).subscribe({
      next: (task) => {
        this.task = task;
        this.isLoading = false;
        this.projectService.getById(task.projectId).subscribe({
          next: (project) => (this.projectMembers = project.members),
          error: () => undefined
        });
      },
      error: (err: ApiError) => {
        this.errorMessage = err.status === 404
          ? 'Tarefa não encontrada.'
          : 'Não foi possível carregar a tarefa.';
        this.isLoading = false;
      }
    });
  }

  startEdit(): void {
    if (!this.task) {
      return;
    }
    this.editForm = {
      title: this.task.title,
      description: this.task.description ?? '',
      type: this.task.type,
      priority: this.task.priority,
      assigneeId: this.task.assignee?.id ?? null,
      collaboratorIds: this.task.collaborators.map(c => c.id),
      dueDate: this.task.dueDate ?? '',
      estimate: this.task.estimate ?? null
    };
    this.isEditing = true;
  }

  cancelEdit(): void {
    this.isEditing = false;
  }

  toggleCollaborator(userId: number): void {
    const index = this.editForm.collaboratorIds.indexOf(userId);
    if (index === -1) {
      this.editForm.collaboratorIds.push(userId);
    } else {
      this.editForm.collaboratorIds.splice(index, 1);
    }
  }

  saveEdit(): void {
    if (!this.task || this.editForm.title.trim().length < 3) {
      return;
    }

    this.isSaving = true;
    this.taskService.update(this.taskId, {
      title: this.editForm.title.trim(),
      description: this.editForm.description || undefined,
      type: this.editForm.type,
      priority: this.editForm.priority,
      assigneeId: this.editForm.assigneeId ?? undefined,
      collaboratorIds: this.editForm.collaboratorIds,
      dueDate: this.editForm.dueDate || undefined,
      estimate: this.editForm.estimate ?? undefined
    }).subscribe({
      next: (task) => {
        this.task = task;
        this.isSaving = false;
        this.isEditing = false;
      },
      error: (err: ApiError) => {
        this.isSaving = false;
        this.errorMessage = err.message;
      }
    });
  }

  archive(): void {
    if (!this.task) {
      return;
    }
    if (!confirm(`Arquivar a tarefa "${this.task.title}"? O histórico será preservado.`)) {
      return;
    }

    this.isArchiving = true;
    const boardId = this.task.boardId;
    this.taskService.archive(this.taskId).subscribe({
      next: () => this.router.navigate(['/boards', boardId]),
      error: (err: ApiError) => {
        this.isArchiving = false;
        this.errorMessage = err.message;
      }
    });
  }

  addComment(): void {
    const content = this.newCommentContent.trim();
    if (!content || this.isSubmittingComment || !this.task) {
      return;
    }

    this.isSubmittingComment = true;
    this.taskService.createComment(this.taskId, { content }).subscribe({
      next: (comment) => {
        this.task!.comments.push(comment);
        this.newCommentContent = '';
        this.isSubmittingComment = false;
      },
      error: (err: ApiError) => {
        this.isSubmittingComment = false;
        this.errorMessage = err.message;
      }
    });
  }

  isMine(comment: CommentResponse): boolean {
    return comment.author.id === this.currentUserId;
  }

  startEditComment(comment: CommentResponse): void {
    this.editingCommentId = comment.id;
    this.editingCommentContent = comment.content;
  }

  cancelEditComment(): void {
    this.editingCommentId = null;
  }

  saveEditComment(comment: CommentResponse): void {
    const content = this.editingCommentContent.trim();
    if (!content) {
      return;
    }

    this.taskService.updateComment(comment.id, { content }).subscribe({
      next: (updated) => {
        const index = this.task!.comments.findIndex(c => c.id === comment.id);
        if (index !== -1) {
          this.task!.comments[index] = updated;
        }
        this.editingCommentId = null;
      },
      error: (err: ApiError) => (this.errorMessage = err.message)
    });
  }

  deleteComment(comment: CommentResponse): void {
    if (!confirm('Excluir este comentário?')) {
      return;
    }

    this.taskService.deleteComment(comment.id).subscribe({
      next: () => {
        this.task!.comments = this.task!.comments.filter(c => c.id !== comment.id);
      },
      error: (err: ApiError) => (this.errorMessage = err.message)
    });
  }

  activityDescription(activity: ActivityResponse): string {
    return activity.author
      ? `${activity.author.username} — ${activity.description}`
      : activity.description;
  }

  priorityLabel(priority: TaskPriority): string {
    return PRIORITY_LABELS[priority];
  }

  priorityClass(priority: TaskPriority): string {
    return PRIORITY_PILL_CLASSES[priority];
  }

  typeLabel(type: TaskType): string {
    return TYPE_LABELS[type];
  }

  initials(name: string): string {
    return name.slice(0, 2).toUpperCase();
  }

  private emptyForm(): TaskEditForm {
    return {
      title: '',
      description: '',
      type: 'OTHER',
      priority: 'MEDIUM',
      assigneeId: null,
      collaboratorIds: [],
      dueDate: '',
      estimate: null
    };
  }
}
