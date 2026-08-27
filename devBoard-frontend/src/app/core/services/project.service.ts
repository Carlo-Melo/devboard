import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateProjectRequest,
  ProjectResponse,
  ProjectSummaryResponse,
  UpdateProjectRequest
} from '../models/project.models';
import { PageResponse } from '../models/page-response.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ProjectService {

  private readonly apiUrl = `${environment.apiUrl}/projects`;

  constructor(private http: HttpClient) {}

  create(request: CreateProjectRequest): Observable<ProjectResponse> {
    return this.http.post<ProjectResponse>(this.apiUrl, request);
  }

  list(archived = false, page = 0, size = 20): Observable<PageResponse<ProjectSummaryResponse>> {
    const params = new HttpParams()
      .set('archived', archived)
      .set('page', page)
      .set('size', size);
    return this.http.get<PageResponse<ProjectSummaryResponse>>(this.apiUrl, { params });
  }

  getById(projectId: number): Observable<ProjectResponse> {
    return this.http.get<ProjectResponse>(`${this.apiUrl}/${projectId}`);
  }

  update(projectId: number, request: UpdateProjectRequest): Observable<ProjectResponse> {
    return this.http.put<ProjectResponse>(`${this.apiUrl}/${projectId}`, request);
  }

  archive(projectId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${projectId}`);
  }
}
