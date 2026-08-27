import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BoardViewComponent } from './board-view.component';
import { BoardService } from '../../../core/services/board.service';
import { BoardResponse } from '../../../core/models/board.models';
import { ApiError } from '../../../core/models/api-error.model';

describe('BoardViewComponent', () => {
  let boardServiceSpy: jasmine.SpyObj<BoardService>;

  function boardResponse(overrides: Partial<BoardResponse> = {}): BoardResponse {
    return {
      id: 1,
      projectId: 7,
      name: 'Main Board',
      defaultBoard: true,
      columns: [
        { id: 1, name: 'Backlog', position: 0, role: 'BACKLOG', taskCount: 0 },
        { id: 2, name: 'To-Do', position: 1, role: 'TODO', taskCount: 3, wipLimit: 2 }
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides
    };
  }

  beforeEach(async () => {
    boardServiceSpy = jasmine.createSpyObj('BoardService', ['getById']);

    await TestBed.configureTestingModule({
      imports: [BoardViewComponent],
      providers: [
        { provide: BoardService, useValue: boardServiceSpy },
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
});
