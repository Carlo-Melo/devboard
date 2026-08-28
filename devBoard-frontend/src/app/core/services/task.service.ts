import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActivityResponse,
  CommentResponse,
  CreateCommentRequest,
  CreateTaskRequest,
  MoveTaskRequest,
  TaskActivityType,
  TaskResponse,
  UpdateCommentRequest,
  UpdateTaskRequest
} from '../models/task.models';
import { PageResponse } from '../models/page-response.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class TaskService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  create(request: CreateTaskRequest): Observable<TaskResponse> {
    return this.http.post<TaskResponse>(`${this.apiUrl}/tasks`, request);
  }

  getById(taskId: number): Observable<TaskResponse> {
    return this.http.get<TaskResponse>(`${this.apiUrl}/tasks/${taskId}`);
  }

  update(taskId: number, request: UpdateTaskRequest): Observable<TaskResponse> {
    return this.http.put<TaskResponse>(`${this.apiUrl}/tasks/${taskId}`, request);
  }

  move(taskId: number, request: MoveTaskRequest): Observable<TaskResponse> {
    return this.http.put<TaskResponse>(`${this.apiUrl}/tasks/${taskId}/move`, request);
  }

  archive(taskId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/tasks/${taskId}`);
  }

  createComment(taskId: number, request: CreateCommentRequest): Observable<CommentResponse> {
    return this.http.post<CommentResponse>(`${this.apiUrl}/tasks/${taskId}/comments`, request);
  }

  listComments(taskId: number, page = 0, size = 20): Observable<PageResponse<CommentResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<CommentResponse>>(`${this.apiUrl}/tasks/${taskId}/comments`, { params });
  }

  updateComment(commentId: number, request: UpdateCommentRequest): Observable<CommentResponse> {
    return this.http.put<CommentResponse>(`${this.apiUrl}/comments/${commentId}`, request);
  }

  deleteComment(commentId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/comments/${commentId}`);
  }

  listActivities(taskId: number, type?: TaskActivityType, page = 0, size = 20): Observable<PageResponse<ActivityResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<PageResponse<ActivityResponse>>(`${this.apiUrl}/tasks/${taskId}/activities`, { params });
  }
}
