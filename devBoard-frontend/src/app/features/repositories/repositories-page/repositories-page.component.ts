import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, startWith, switchMap } from 'rxjs';
import { UserResponse } from '../../../core/models/auth.models';
import { GithubRepoResponse, GithubRepoPage } from '../../../core/models/github.models';
import { AuthService } from '../../../core/services/auth.service';
import { GithubService } from '../../../core/services/github.service';
import { LinkRepositoryDialogComponent } from '../link-repository-dialog/link-repository-dialog.component';
import { RepositoryCardComponent } from '../repository-card/repository-card.component';

@Component({selector:'app-repositories-page',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink,RepositoryCardComponent,LinkRepositoryDialogComponent],templateUrl:'./repositories-page.component.html',styleUrl:'./repositories-page.component.scss'})
export class RepositoriesPageComponent implements OnInit {
 private destroyRef=inject(DestroyRef); private query$=new Subject<{search:string;page:number}>(); currentUser:UserResponse|null=null; repositories:GithubRepoResponse[]=[]; page=0; totalPages=0; totalElements=0; loading=true; error=''; retryAfterSeconds=0; activeRepo:GithubRepoResponse|null=null; private trigger:HTMLElement|null=null; private retryTimer:number|undefined;
 search=new FormControl('',{nonNullable:true});
 constructor(private auth:AuthService,private github:GithubService){}
 ngOnInit():void {this.auth.getMe().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:u=>{this.currentUser=u;if(u.githubConnected)this.search.valueChanges.pipe(startWith(this.search.value),debounceTime(300),distinctUntilChanged(),takeUntilDestroyed(this.destroyRef)).subscribe(q=>{if(!this.retryAfterSeconds)this.query$.next({search:q,page:0});});else this.loading=false;},error:()=>this.loading=false}); this.query$.pipe(switchMap(q=>{this.loading=true;this.error='';return this.github.availableRepositories(q.search,q.page,20).pipe(catchError((e:any)=>{this.error=e?.message||'Não foi possível carregar seus repositórios.';this.startRetryAfter(e?.retryAfterSeconds);return of(null);}));}),takeUntilDestroyed(this.destroyRef)).subscribe((r:GithubRepoPage|null)=>{if(r){this.repositories=r.content;this.page=r.page;this.totalPages=r.totalPages;this.totalElements=r.totalElements;}this.loading=false;}); this.destroyRef.onDestroy(()=>window.clearInterval(this.retryTimer)); }
 loadPage(page:number):void{if(!this.retryAfterSeconds)this.query$.next({search:this.search.value,page});}
 refresh():void{if(!this.retryAfterSeconds)this.query$.next({search:this.search.value,page:this.page});}
 private startRetryAfter(seconds=0):void{window.clearInterval(this.retryTimer);this.retryAfterSeconds=Math.max(0,seconds||0);if(this.retryAfterSeconds)this.retryTimer=window.setInterval(()=>{this.retryAfterSeconds=Math.max(0,this.retryAfterSeconds-1);if(!this.retryAfterSeconds)window.clearInterval(this.retryTimer);},1000);}
 openLink(repo:GithubRepoResponse,event:MouseEvent):void{this.trigger=event.currentTarget as HTMLElement;this.activeRepo=repo;}
 closeDialog():void{this.activeRepo=null;setTimeout(()=>this.trigger?.focus(),0);}
 onLinked(id:number):void{this.repositories=this.repositories.map(r=>r.id===id?{...r,linked:true}:r);}
}
