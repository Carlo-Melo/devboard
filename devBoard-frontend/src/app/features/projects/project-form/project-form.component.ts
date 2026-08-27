import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProjectService } from '../../../core/services/project.service';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-project-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './project-form.component.html',
  styleUrl: './project-form.component.scss'
})
export class ProjectFormComponent implements OnInit {

  form: FormGroup;
  isLoading = false;
  isSaving = false;
  errorMessage: string | null = null;
  projectId: number | null = null;

  get isEditMode(): boolean {
    return this.projectId !== null;
  }

  get f() {
    return this.form.controls;
  }

  constructor(
    private fb: FormBuilder,
    private projectService: ProjectService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
      description: ['']
    });
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.projectId = Number(idParam);
      this.loadProject(this.projectId);
    }
  }

  private loadProject(id: number): void {
    this.isLoading = true;
    this.projectService.getById(id).subscribe({
      next: (project) => {
        this.form.patchValue({ name: project.name, description: project.description });
        this.isLoading = false;
      },
      error: (err: ApiError) => {
        this.errorMessage = err.message;
        this.isLoading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = null;

    const request = this.form.value;
    const operation = this.isEditMode
      ? this.projectService.update(this.projectId!, request)
      : this.projectService.create(request);

    operation.subscribe({
      next: (project) => {
        this.isSaving = false;
        this.router.navigate(['/projects', project.id]);
      },
      error: (err: ApiError) => {
        this.isSaving = false;
        this.errorMessage = err.message;

        err.fieldErrors?.forEach(fieldError => {
          const control = this.form.get(fieldError.field);
          if (control) {
            control.setErrors({ ...(control.errors ?? {}), server: fieldError.message });
          }
        });
      }
    });
  }
}
