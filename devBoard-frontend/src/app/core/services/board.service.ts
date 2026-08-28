import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BoardColumnResponse,
  BoardResponse,
  CreateBoardRequest,
  CreateColumnRequest,
  ReorderColumnsRequest,
  UpdateBoardRequest,
  UpdateColumnRequest
} from '../models/board.models';
import { TaskPriority, TaskType } from '../models/task.models';
import { environment } from '../../../environments/environment';

export interface BoardViewFilters {
  assigneeId?: number;
  priority?: TaskPriority;
  type?: TaskType;
  search?: string;
}

@Injectable({ providedIn: 'root' })
export class BoardService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  listByProject(projectId: number): Observable<BoardResponse[]> {
    return this.http.get<BoardResponse[]>(`${this.apiUrl}/projects/${projectId}/boards`);
  }

  create(projectId: number, request: CreateBoardRequest): Observable<BoardResponse> {
    return this.http.post<BoardResponse>(`${this.apiUrl}/projects/${projectId}/boards`, request);
  }

  getById(boardId: number, filters?: BoardViewFilters): Observable<BoardResponse> {
    let params = new HttpParams();
    if (filters?.assigneeId !== undefined) {
      params = params.set('assigneeId', filters.assigneeId);
    }
    if (filters?.priority) {
      params = params.set('priority', filters.priority);
    }
    if (filters?.type) {
      params = params.set('type', filters.type);
    }
    if (filters?.search) {
      params = params.set('search', filters.search);
    }
    return this.http.get<BoardResponse>(`${this.apiUrl}/boards/${boardId}`, { params });
  }

  update(boardId: number, request: UpdateBoardRequest): Observable<BoardResponse> {
    return this.http.put<BoardResponse>(`${this.apiUrl}/boards/${boardId}`, request);
  }

  delete(boardId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/boards/${boardId}`);
  }

  createColumn(boardId: number, request: CreateColumnRequest): Observable<BoardColumnResponse> {
    return this.http.post<BoardColumnResponse>(`${this.apiUrl}/boards/${boardId}/columns`, request);
  }

  updateColumn(columnId: number, request: UpdateColumnRequest): Observable<BoardColumnResponse> {
    return this.http.put<BoardColumnResponse>(`${this.apiUrl}/columns/${columnId}`, request);
  }

  deleteColumn(columnId: number, moveTasksTo?: number): Observable<void> {
    let params = new HttpParams();
    if (moveTasksTo !== undefined) {
      params = params.set('moveTasksTo', moveTasksTo);
    }
    return this.http.delete<void>(`${this.apiUrl}/columns/${columnId}`, { params });
  }

  reorderColumns(boardId: number, request: ReorderColumnsRequest): Observable<BoardColumnResponse[]> {
    return this.http.put<BoardColumnResponse[]>(`${this.apiUrl}/boards/${boardId}/columns/reorder`, request);
  }
}
