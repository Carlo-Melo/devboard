import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProjectMemberResponse, ProjectRole } from '../models/project.models';
import { CreateEmailInviteRequest, GithubImportResponse, InvitePublicResponse, InviteResponse, InviteStatus } from '../models/member.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MemberService {

  private readonly projectsUrl = `${environment.apiUrl}/projects`;
  private readonly invitesUrl = `${environment.apiUrl}/invites`;

  constructor(private http: HttpClient) {}

  list(projectId: number): Observable<ProjectMemberResponse[]> {
    return this.http.get<ProjectMemberResponse[]>(`${this.projectsUrl}/${projectId}/members`);
  }

  inviteByEmail(projectId: number, request: CreateEmailInviteRequest): Observable<InviteResponse> {
    return this.http.post<InviteResponse>(`${this.projectsUrl}/${projectId}/members/invite`, request);
  }

  createInviteLink(projectId: number, role: ProjectRole): Observable<InviteResponse> {
    return this.http.post<InviteResponse>(`${this.projectsUrl}/${projectId}/members/invite-link`, { role });
  }

  importGithub(projectId: number, role: ProjectRole, logins?: string[]): Observable<GithubImportResponse> {
    return this.http.post<GithubImportResponse>(`${this.projectsUrl}/${projectId}/members/import-github`, { role, logins });
  }

  listInvites(projectId: number, status?: InviteStatus): Observable<InviteResponse[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<InviteResponse[]>(`${this.projectsUrl}/${projectId}/invites`, { params });
  }

  updateRole(projectId: number, memberId: number, role: ProjectRole): Observable<ProjectMemberResponse> {
    return this.http.put<ProjectMemberResponse>(`${this.projectsUrl}/${projectId}/members/${memberId}`, { role });
  }

  remove(projectId: number, memberId: number): Observable<void> {
    return this.http.delete<void>(`${this.projectsUrl}/${projectId}/members/${memberId}`);
  }

  leave(projectId: number): Observable<void> {
    return this.http.delete<void>(`${this.projectsUrl}/${projectId}/members/me`);
  }

  getPublicInvite(token: string): Observable<InvitePublicResponse> {
    return this.http.get<InvitePublicResponse>(`${this.invitesUrl}/${token}`);
  }

  acceptInvite(token: string): Observable<ProjectMemberResponse> {
    return this.http.post<ProjectMemberResponse>(`${this.invitesUrl}/accept`, { token });
  }
}
