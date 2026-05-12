import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { AuthApiService } from '../infrastructure/auth-api.service';
import { LoginRequest, RegisterRequest } from '../domain/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthUseCase {
  constructor(private repo: AuthApiService, private router: Router) {}

  login(request: LoginRequest) {
    return this.repo.login(request).pipe(
      tap(response => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('role', response.role);
        this.router.navigate(['/products']);
      })
    );
  }

  register(request: RegisterRequest) {
    return this.repo.register(request).pipe(
      tap(response => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('role', response.role);
        this.router.navigate(['/products']);
      })
    );
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    this.router.navigate(['/auth/login']);
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getRole(): string | null {
    return localStorage.getItem('role');
  }
}