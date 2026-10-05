import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '../models/api-error.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const body = error.error ?? {};

      const apiError: ApiError = {
        status: error.status,
        message: body.message ?? 'Erro inesperado. Tente novamente.',
        fieldErrors: body.fieldErrors,
        retryAfterSeconds: error.headers.get('Retry-After') ? Number(error.headers.get('Retry-After')) : undefined
      };

      if (error.status === 401) {
        localStorage.removeItem('devboardToken');
      }

      return throwError(() => apiError);
    })
  );
};
