import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AppShellComponent } from './shared/components/app-shell/app-shell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, AppShellComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'devBoard-frontend';
  constructor(public router: Router) {}
  get isWorkspace(): boolean {
    return /^\/(projects|boards|tasks)(\/|\?|$)/.test(this.router.url);
  }
}
