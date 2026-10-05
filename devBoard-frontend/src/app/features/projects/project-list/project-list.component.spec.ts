import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { ProjectListComponent } from './project-list.component';
import { ProjectService } from '../../../core/services/project.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { GithubService } from '../../../core/services/github.service';
import { PageResponse } from '../../../core/models/page-response.model';
import { ProjectSummaryResponse } from '../../../core/models/project.models';

const owner = { id:1, username:'ana', email:'ana@example.com', authProvider:'TRADITIONAL' as const, githubConnected:false };
const project:ProjectSummaryResponse = { id:7, name:'Projeto Alpha', description:'Nossa próxima entrega', owner, memberCount:2, boardCount:3, githubBoardCount:1, updatedAt:'2026-10-01T10:00:00' };
function pageOf(content:ProjectSummaryResponse[], page=0, total=content.length):PageResponse<ProjectSummaryResponse> {
  return {content,page,size:12,totalElements:total,totalPages:Math.ceil(total/12),first:page===0,last:(page+1)*12>=total};
}

describe('ProjectListComponent',()=>{
  let projects:jasmine.SpyObj<ProjectService>;
  let dashboard:jasmine.SpyObj<DashboardService>;
  let github:jasmine.SpyObj<GithubService>;
  beforeEach(async()=>{
    projects=jasmine.createSpyObj('ProjectService',['list']);
    projects.list.and.returnValue(of(pageOf([project])));
    dashboard=jasmine.createSpyObj('DashboardService',['summary','tasks','activities']);
    github=jasmine.createSpyObj('GithubService',['availableRepositories']);
    await TestBed.configureTestingModule({imports:[ProjectListComponent],providers:[
      provideRouter([]),{provide:ProjectService,useValue:projects},{provide:DashboardService,useValue:dashboard},{provide:GithubService,useValue:github}
    ]}).compileComponents();
  });
  function create(){const f=TestBed.createComponent(ProjectListComponent);f.detectChanges();return f;}
  it('renders projects immediately and never loads dashboard or GitHub',()=>{
    projects.list.and.returnValue(of(pageOf([project],0,27)));
    const f=create();
    expect(f.nativeElement.querySelector('h1').textContent).toBe('Meus projetos');
    expect(f.nativeElement.textContent).toContain('27 projetos');
    expect(f.nativeElement.querySelector('.project-card').getAttribute('href')).toBe('/projects/7');
    expect(f.nativeElement.textContent).toContain('3 pessoas');
    expect(f.nativeElement.textContent).toContain('3 quadros · 1 com GitHub');
    expect(f.nativeElement.querySelector('.metrics-grid')).toBeNull();
    expect(f.nativeElement.querySelector('app-repository-card')).toBeNull();
    expect(projects.list).toHaveBeenCalledOnceWith(false,0,12);
    expect(dashboard.summary).not.toHaveBeenCalled();expect(dashboard.tasks).not.toHaveBeenCalled();expect(dashboard.activities).not.toHaveBeenCalled();
    expect(github.availableRepositories).not.toHaveBeenCalled();
  });
  it('shows the loading skeleton and cancels a pending request on destruction',()=>{
    const response=new Subject<PageResponse<ProjectSummaryResponse>>();projects.list.and.returnValue(response);
    const f=create();expect(f.nativeElement.querySelector('.projects-skeleton')).toBeTruthy();
    f.destroy();expect(response.observed).toBeFalse();
  });
  it('shows the empty state with a link to create a project',()=>{
    projects.list.and.returnValue(of(pageOf([])));const f=create();
    expect(f.nativeElement.querySelector('.empty-state')).toBeTruthy();
    expect(f.nativeElement.querySelector('.empty-state a').getAttribute('href')).toBe('/projects/new');
  });
  it('allows retry after an error',()=>{
    projects.list.and.returnValues(throwError(()=>({status:503})),of(pageOf([project])));
    const f=create();expect(f.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    f.nativeElement.querySelector('[role="alert"] button').click();f.detectChanges();
    expect(f.nativeElement.querySelector('.project-card')).toBeTruthy();expect(f.componentInstance.errorMessage).toBeNull();
  });
  it('paginates through the backend and respects the first and last pages',()=>{
    projects.list.and.callFake((_archived,page=0)=>of(pageOf([project],page,13)));
    const f=create();f.componentInstance.previousPage();expect(projects.list).toHaveBeenCalledTimes(1);
    f.componentInstance.nextPage();f.detectChanges();expect(projects.list).toHaveBeenCalledWith(false,1,12);
    expect(f.componentInstance.page).toBe(1);f.componentInstance.nextPage();expect(projects.list).toHaveBeenCalledTimes(2);
    f.componentInstance.previousPage();expect(f.componentInstance.page).toBe(0);
  });
});