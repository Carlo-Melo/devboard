import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectSummaryResponse } from '../../../core/models/project.models';

@Component({
  selector: 'app-project-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './project-card.component.html',
  styles: [':host { display: block; min-width: 0; height: 100%; } .project-card { height: 100%; } .project-card-meta { flex-wrap: wrap; gap: 8px; } .pill { max-width: 100%; white-space: normal; } .project-card:focus-visible { outline: 2px solid var(--color-primary-500); outline-offset: 3px; }']
})
export class ProjectCardComponent {
  @Input({ required: true }) project!: ProjectSummaryResponse;
}
