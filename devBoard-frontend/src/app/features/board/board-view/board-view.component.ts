import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  CdkDrag,
  CdkDragDrop,
  CdkDropList,
  CdkDropListGroup,
  moveItemInArray,
  transferArrayItem
} from '@angular/cdk/drag-drop';
import { BoardService, BoardViewFilters } from '../../../core/services/board.service';
import { TaskService } from '../../../core/services/task.service';
import { BoardColumnResponse, BoardResponse, ColumnRole } from '../../../core/models/board.models';
import { TaskPriority, TaskSummaryResponse, TaskType } from '../../../core/models/task.models';
import { ApiError } from '../../../core/models/api-error.model';
import { PRIORITY_LABELS, PRIORITY_PILL_CLASSES, TYPE_LABELS } from '../../../shared/constants/task-labels';

interface ColumnForm {
  name: string;
  color: string;
  role: ColumnRole;
  wipLimit: number | null;
}
function emptyColumnForm(): ColumnForm {
  return { name: '', color: '', role: 'NONE', wipLimit: null };
}
@Component({
  selector: 'app-board-view',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, CdkDropListGroup, CdkDropList, CdkDrag],
  templateUrl: './board-view.component.html',
  styleUrl: './board-view.component.scss'
})
export class BoardViewComponent implements OnInit {

  board: BoardResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  search = '';
  typeFilter: TaskType | '' = '';
  priorityFilter: TaskPriority | '' = '';

  addingToColumnId: number | null = null;
  newTaskTitle = '';
  isCreatingTask = false;

  isAddingColumn = false;
  newColumnForm: ColumnForm = emptyColumnForm();

  editingColumnId: number | null = null;
  editColumnForm: ColumnForm = emptyColumnForm();

  deletingColumnId: number | null = null;
  deleteMoveTargetId: number | null = null;

  isSavingColumn = false;

  readonly columnRoles: ColumnRole[] = ['NONE', 'BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];

  private boardId!: number;

  constructor(
    private boardService: BoardService,
    private taskService: TaskService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.boardId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.errorMessage = null;

    const filters: BoardViewFilters = {
      type: this.typeFilter || undefined,
      priority: this.priorityFilter || undefined,
      search: this.search || undefined
    };

    this.boardService.getById(this.boardId, filters).subscribe({
      next: (board) => {
        this.board = board;
        this.isLoading = false;
      },
      error: (err: ApiError) => {
        this.errorMessage = err.status === 404
          ? 'Quadro não encontrado.'
          : 'Não foi possível carregar o quadro.';
        this.isLoading = false;
      }
    });
  }

  onFilterChange(): void {
    this.load();
  }

  isOverWip(columnCount: number, wipLimit: number | undefined): boolean {
    return !!wipLimit && columnCount > wipLimit;
  }

  columnDropListId(columnId: number): string {
    return 'column-' + columnId;
  }

  connectedDropLists(): string[] {
    return this.board?.columns.map(c => this.columnDropListId(c.id)) ?? [];
  }

  /** Bloqueia soltar numa coluna que já atingiu o limite WIP (spec-board-kanban.md — WIP bloqueia movimentação manual). */
  canEnterColumn = (column: BoardColumnResponse) => (_drag: CdkDrag, drop: CdkDropList<TaskSummaryResponse[]>): boolean => {
    if (!column.wipLimit || drop.data === column.tasks) {
      return true;
    }
    return column.tasks.length < column.wipLimit;
  };

  drop(event: CdkDragDrop<TaskSummaryResponse[]>, targetColumn: BoardColumnResponse): void {
    const task = event.previousContainer.data[event.previousIndex];
    const sourceColumn = this.board?.columns.find(c => c.tasks === event.previousContainer.data);

    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) {
        return;
      }
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
      targetColumn.taskCount = targetColumn.tasks.length;
      if (sourceColumn) {
        sourceColumn.taskCount = sourceColumn.tasks.length;
      }
    }

    this.taskService.move(task.id, { columnId: targetColumn.id, position: event.currentIndex }).subscribe({
      error: () => {
        this.errorMessage = 'Não foi possível mover a tarefa — o quadro foi recarregado.';
        this.load();
      }
    });
  }

  startAddTask(columnId: number): void {
    this.addingToColumnId = columnId;
    this.newTaskTitle = '';
  }

  cancelAddTask(): void {
    this.addingToColumnId = null;
    this.newTaskTitle = '';
  }

  submitAddTask(column: BoardColumnResponse): void {
    const title = this.newTaskTitle.trim();
    if (title.length < 3 || this.isCreatingTask) {
      return;
    }

    this.isCreatingTask = true;
    this.taskService.create({ columnId: column.id, title }).subscribe({
      next: (task) => {
        column.tasks.push({
          id: task.id,
          title: task.title,
          type: task.type,
          priority: task.priority,
          assignee: task.assignee,
          dueDate: task.dueDate,
          estimate: task.estimate,
          position: task.position,
          commentCount: 0
        });
        column.taskCount = column.tasks.length;
        this.isCreatingTask = false;
        this.newTaskTitle = '';
      },
      error: () => {
        this.isCreatingTask = false;
        this.errorMessage = 'Não foi possível criar a tarefa.';
      }
    });
  }

  goToTask(taskId: number): void {
    this.router.navigate(['/tasks', taskId]);
  }

  onTaskKeydown(event: Event, taskId: number): void {
    if (event.target === event.currentTarget) this.goToTask(taskId);
  }

  archiveTask(column: BoardColumnResponse, task: TaskSummaryResponse, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Arquivar a tarefa "${task.title}"?`)) {
      return;
    }

    this.taskService.archive(task.id).subscribe({
      next: () => {
        column.tasks = column.tasks.filter(t => t.id !== task.id);
        column.taskCount = column.tasks.length;
      },
      error: (err: ApiError) => (this.errorMessage = err.message)
    });
  }

  // ---- colunas ----

  startAddColumn(): void {
    this.newColumnForm = emptyColumnForm();
    this.isAddingColumn = true;
  }

  cancelAddColumn(): void {
    this.isAddingColumn = false;
  }

  submitAddColumn(): void {
    if (!this.board || this.newColumnForm.name.trim().length === 0 || this.isSavingColumn) {
      return;
    }

    this.isSavingColumn = true;
    this.boardService.createColumn(this.board.id, {
      name: this.newColumnForm.name.trim(),
      color: this.newColumnForm.color || undefined,
      role: this.newColumnForm.role,
      wipLimit: this.newColumnForm.wipLimit ?? undefined
    }).subscribe({
      next: () => {
        this.isSavingColumn = false;
        this.isAddingColumn = false;
        this.load();
      },
      error: (err: ApiError) => {
        this.isSavingColumn = false;
        this.errorMessage = err.message;
      }
    });
  }

  startEditColumn(column: BoardColumnResponse): void {
    this.editingColumnId = column.id;
    this.editColumnForm = {
      name: column.name,
      color: column.color ?? '',
      role: column.role,
      wipLimit: column.wipLimit ?? null
    };
  }

  cancelEditColumn(): void {
    this.editingColumnId = null;
  }

  submitEditColumn(column: BoardColumnResponse): void {
    if (this.editColumnForm.name.trim().length === 0 || this.isSavingColumn) {
      return;
    }

    this.isSavingColumn = true;
    this.boardService.updateColumn(column.id, {
      name: this.editColumnForm.name.trim(),
      color: this.editColumnForm.color || undefined,
      role: this.editColumnForm.role,
      wipLimit: this.editColumnForm.wipLimit ?? undefined
    }).subscribe({
      next: () => {
        this.isSavingColumn = false;
        this.editingColumnId = null;
        this.load();
      },
      error: (err: ApiError) => {
        this.isSavingColumn = false;
        this.errorMessage = err.message;
      }
    });
  }

  startDeleteColumn(column: BoardColumnResponse): void {
    this.deletingColumnId = column.id;
    this.deleteMoveTargetId = null;
  }

  cancelDeleteColumn(): void {
    this.deletingColumnId = null;
  }

  otherColumns(column: BoardColumnResponse): BoardColumnResponse[] {
    return this.board?.columns.filter(c => c.id !== column.id) ?? [];
  }

  confirmDeleteColumn(column: BoardColumnResponse): void {
    if (column.taskCount > 0 && this.deleteMoveTargetId === null) {
      return;
    }

    this.isSavingColumn = true;
    this.boardService.deleteColumn(column.id, this.deleteMoveTargetId ?? undefined).subscribe({
      next: () => {
        this.isSavingColumn = false;
        this.deletingColumnId = null;
        this.load();
      },
      error: (err: ApiError) => {
        this.isSavingColumn = false;
        this.errorMessage = err.message;
      }
    });
  }

  roleLabel(role: ColumnRole): string {
    return ({ NONE: 'Sem etapa definida', BACKLOG: 'Backlog', TODO: 'A fazer', IN_PROGRESS: 'Em andamento', IN_REVIEW: 'Em revisão', DONE: 'Concluído' })[role];
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
}
