import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BoardService } from './board.service';
import { environment } from '../../../environments/environment';

describe('BoardService lifecycle', () => {
  let service: BoardService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); service = TestBed.inject(BoardService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('archives through a dedicated POST endpoint', () => {
    service.archive(2).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/boards/2/archive`); expect(request.request.method).toBe('POST'); request.flush(null);
  });
  it('restores through a dedicated POST endpoint', () => {
    service.restore(2).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/boards/2/restore`); expect(request.request.method).toBe('POST'); request.flush({ id: 2, archived: false });
  });
  it('loads archived boards with backend pagination', () => {
    service.listArchived(1, 2, 20).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/projects/1/boards/archived?page=2&size=20`); expect(request.request.method).toBe('GET'); request.flush({ content: [] });
  });
});
