import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { GithubRepoResponse, GithubSettingsRequest, GithubSettingsResponse } from '../models/github.models';
@Injectable({ providedIn: 'root' })
export class GithubService {
  private readonly url = environment.apiUrl;
  constructor(private http: HttpClient) {}
  repositories(search = '') { return this.http.get<GithubRepoResponse[]>(`${this.url}/github-repos`, { params: new HttpParams().set('search', search) }); }
  settings(id: number) { return this.http.get<GithubSettingsResponse>(`${this.url}/boards/${id}/github-settings`); }
  update(id: number, request: GithubSettingsRequest) { return this.http.put<GithubSettingsResponse>(`${this.url}/boards/${id}/github-settings`, request); }
  link(id: number, githubRepoId: number, defaultBaseBranch: string, watchedBranches: string[]) { return this.http.post<GithubSettingsResponse>(`${this.url}/boards/${id}/link-github`, { githubRepoId, defaultBaseBranch, watchedBranches }); }
  unlink(id: number) { return this.http.delete<void>(`${this.url}/boards/${id}/link-github`); }
  sync(id: number) { return this.http.post<void>(`${this.url}/boards/${id}/sync-github`, {}); }
}
