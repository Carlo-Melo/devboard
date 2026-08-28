import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BoardViewComponent } from './board-view.component';
import { BoardService } from '../../../core/services/board.service';
import { TaskService } from '../../../core/services/task.service';
import { BoardResponse } from '../../../core/models/board.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('BoardViewComponent', () => {
  let boardServiceSpy: jasmine.SpyObj<BoardService>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;

  function boardResponse(overrides: Partial<BoardResponse> = {}): BoardResponse {
    return {
      id: 1,
      projectId: 7,
      name: 'Main Board',
      defaultBoard: true,
      columns: [
        { id: 1, name: 'Backlog', position: 0, role: 'BACKLOG', taskCount: 0, tasks: [] },
        { id: 2, name: 'To-Do', position: 1, role: 'TODO', taskCount: 3, wipLimit: 2, tasks: [] }
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides
    };
  }

  beforeEach(async () => {
    boardServiceSpy = jasmine.createSpyObj('BoardService', ['getById', 'createColumn', 'updateColumn', 'deleteColumn']);
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['create', 'move', 'archive']);

    await TestBed.configureTestingModule({
      imports: [BoardViewComponent],
      providers: [
        { provide: BoardService, useValue: boardServiceSpy },
        { provide: TaskService, useValue: taskServiceSpy },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } }
      ]
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(BoardViewComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should load the board on init', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));

    const fixture = createComponent();

    expect(fixture.componentInstance.board?.columns.length).toBe(2);
    expect(fixture.componentInstance.isLoading).toBeFalse();
  });

  it('should show a not-found message on 404', () => {
    const apiError: ApiError = { status: 404, message: 'Quadro não encontrado' };
    boardServiceSpy.getById.and.returnValue(throwError(() => apiError));

    const fixture = createComponent();

    expect(fixture.componentInstance.errorMessage).toBe('Quadro não encontrado.');
  });

  it('should flag a column as over its WIP limit', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();

    expect(fixture.componentInstance.isOverWip(3, 2)).toBeTrue();
    expect(fixture.componentInstance.isOverWip(1, 2)).toBeFalse();
    expect(fixture.componentInstance.isOverWip(5, undefined)).toBeFalse();
  });

  it('should append the created task to the column on quick-add', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const column = fixture.componentInstance.board!.columns[0];
    taskServiceSpy.create.and.returnValue(of({
      id: 42,
      title: 'Nova tarefa',
      type: 'OTHER',
      priority: 'MEDIUM',
      position: 0
    } as any));

    fixture.componentInstance.startAddTask(column.id);
    fixture.componentInstance.newTaskTitle = 'Nova tarefa';
    fixture.componentInstance.submitAddTask(column);

    expect(column.tasks.length).toBe(1);
    expect(column.tasks[0].id).toBe(42);
    expect(column.taskCount).toBe(1);
    // O formulário de adição permanece aberto para cadastro rápido de várias tarefas em sequência.
    expect(fixture.componentInstance.addingToColumnId).toBe(column.id);
    expect(fixture.componentInstance.newTaskTitle).toBe('');
  });

  it('should block entering a column that reached its WIP limit', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const limited = fixture.componentInstance.board!.columns[1];
    limited.tasks.push({ id: 1 } as any, { id: 2 } as any);

    const predicate = fixture.componentInstance.canEnterColumn(limited);

    expect(predicate({} as any, { data: [] } as any)).toBeFalse();
    expect(predicate({} as any, { data: limited.tasks } as any)).toBeTrue();
  });

  it('should create a column and reload the board', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    boardServiceSpy.createColumn.and.returnValue(of({} as any));

    fixture.componentInstance.startAddColumn();
    fixture.componentInstance.newColumnForm.name = 'Em revisão';
    fixture.componentInstance.submitAddColumn();

    expect(boardServiceSpy.createColumn).toHaveBeenCalledWith(1, jasmine.objectContaining({ name: 'Em revisão' }));
    expect(boardServiceSpy.getById).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.isAddingColumn).toBeFalse();
  });

  it('should update a column and reload the board', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const column = fixture.componentInstance.board!.columns[0];
    boardServiceSpy.updateColumn.and.returnValue(of({} as any));

    fixture.componentInstance.startEditColumn(column);
    fixture.componentInstance.editColumnForm.name = 'Backlog renomeado';
    fixture.componentInstance.submitEditColumn(column);

    expect(boardServiceSpy.updateColumn).toHaveBeenCalledWith(column.id, jasmine.objectContaining({ name: 'Backlog renomeado' }));
    expect(fixture.componentInstance.editingColumnId).toBeNull();
  });

  it('should require a destination column before deleting a column with tasks', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const column = fixture.componentInstance.board!.columns[1];

    fixture.componentInstance.startDeleteColumn(column);
    fixture.componentInstance.confirmDeleteColumn(column);

    expect(boardServiceSpy.deleteColumn).not.toHaveBeenCalled();
  });

  it('should delete an empty column without asking for a destination', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const column = fixture.componentInstance.board!.columns[0];
    boardServiceSpy.deleteColumn.and.returnValue(of(undefined));

    fixture.componentInstance.startDeleteColumn(column);
    fixture.componentInstance.confirmDeleteColumn(column);

    expect(boardServiceSpy.deleteColumn).toHaveBeenCalledWith(column.id, undefined);
    expect(fixture.componentInstance.deletingColumnId).toBeNull();
  });

  it('should remove the task from the column when archived', () => {
    boardServiceSpy.getById.and.returnValue(of(boardResponse()));
    const fixture = createComponent();
    const column = fixture.componentInstance.board!.columns[0];
    const task = { id: 99, title: 'Tarefa X' } as any;
    column.tasks.push(task);
    column.taskCount = 1;
    taskServiceSpy.archive.and.returnValue(of(undefined));
    spyOn(window, 'confirm').and.returnValue(true);
    const event = new Event('click');
    spyOn(event, 'stopPropagation');

    fixture.componentInstance.archiveTask(column, task, event);

    expect(taskServiceSpy.archive).toHaveBeenCalledWith(99);
    expect(column.tasks).not.toContain(task);
    expect(column.taskCount).toBe(0);
    expect(event.stopPropagation).toHaveBeenCalled();
  });
});
