import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { GithubDestination, GithubRepoResponse, GithubRepoPage, GithubSettingsRequest, GithubSettingsResponse } from '../models/github.models';
@Injectable({ providedIn: 'root' })
export class GithubService {
  private readonly url = environment.apiUrl;
  constructor(private http: HttpClient) {}
  repositories(search = '') { return this.http.get<GithubRepoResponse[]>(`${this.url}/github-repos`, { params: new HttpParams().set('search', search) }); }
  publicRepositories(search = '', page = 0, size = 20) { return this.http.get<GithubRepoPage>(`${this.url}/github-repos/public`, { params: new HttpParams().set('search', search).set('page', page).set('size', size) }); }
  availableRepositories(search = '', page = 0, size = 20) { return this.http.get<GithubRepoPage>(`${this.url}/github-repos/available`, { params: new HttpParams().set('search', search).set('page', page).set('size', size) }); }
  destinations(page = 0, size = 100) { return this.http.get<import('../models/page-response.model').PageResponse<GithubDestination>>(`${this.url}/github-repos/destinations`, { params: new HttpParams().set('page', page).set('size', size) }); }
  settings(id: number) { return this.http.get<GithubSettingsResponse>(`${this.url}/boards/${id}/github-settings`); }
  update(id: number, request: GithubSettingsRequest) { return this.http.put<GithubSettingsResponse>(`${this.url}/boards/${id}/github-settings`, request); }
  link(id: number, githubRepoId: number, defaultBaseBranch: string, watchedBranches: string[]) { return this.http.post<GithubSettingsResponse>(`${this.url}/boards/${id}/link-github`, { githubRepoId, defaultBaseBranch, watchedBranches }); }
  unlink(id: number) { return this.http.delete<void>(`${this.url}/boards/${id}/link-github`); }
  sync(id: number) { return this.http.post<void>(`${this.url}/boards/${id}/sync-github`, {}); }
}
