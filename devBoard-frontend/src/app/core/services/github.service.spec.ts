import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { GithubService } from './github.service';
import { environment } from '../../../environments/environment';
describe('GithubService', () => {
  let service: GithubService; let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(GithubService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('links a repository to the selected board with branch configuration', () => {
    service.link(7, 123, 'develop', ['main']).subscribe();
    const r = http.expectOne(`${environment.apiUrl}/boards/7/link-github`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ githubRepoId: 123, defaultBaseBranch: 'develop', watchedBranches: ['main'] });
    r.flush({});
  });
  it('loads write-access repositories with a search query', () => {
    service.repositories('api').subscribe();
    const r = http.expectOne(`${environment.apiUrl}/github-repos?search=api`); r.flush([]);
  });
  it('unlinks only the selected board', () => {
    service.unlink(9).subscribe(); const r = http.expectOne(`${environment.apiUrl}/boards/9/link-github`);
    expect(r.request.method).toBe('DELETE'); r.flush(null);
  });
  it('queues synchronization for the selected board', () => {
    service.sync(9).subscribe(); const r = http.expectOne(`${environment.apiUrl}/boards/9/sync-github`);
    expect(r.request.method).toBe('POST'); r.flush(null, { status: 202, statusText: 'Accepted' });
  });
});
