import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function passwordMatchValidator(passwordField = 'password', confirmField = 'confirmPassword'): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const confirmControl = group.get(confirmField);
    if (!confirmControl) {
      return null;
    }

    const password = group.get(passwordField)?.value;
    const confirmPassword = confirmControl.value;
    const { passwordMismatch, ...otherErrors } = confirmControl.errors ?? {};

    if (password === confirmPassword) {
      confirmControl.setErrors(Object.keys(otherErrors).length ? otherErrors : null);
      return null;
    }

    confirmControl.setErrors({ ...otherErrors, passwordMismatch: true });
    return { passwordMismatch: true };
  };
}
