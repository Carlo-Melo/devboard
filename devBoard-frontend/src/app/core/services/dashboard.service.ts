import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { DashboardActivityPage, DashboardSummary, DashboardTaskPage } from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly url = `${environment.apiUrl}/dashboard`;
  constructor(private http: HttpClient) {}
  summary(projectId: number | null) { return this.http.get<DashboardSummary>(`${this.url}/summary`, { params: this.params(projectId) }); }
  tasks(projectId: number | null, page = 0) { return this.http.get<DashboardTaskPage>(`${this.url}/my-tasks`, { params: this.params(projectId).set('page', page).set('size', 8) }); }
  activities(projectId: number | null, page = 0) { return this.http.get<DashboardActivityPage>(`${this.url}/activities`, { params: this.params(projectId).set('page', page).set('size', 6) }); }
  private params(projectId: number | null) { return projectId === null ? new HttpParams() : new HttpParams().set('projectId', projectId); }
}
