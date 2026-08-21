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

  /**
   * The current user's id, read from the JWT `sub` claim (JwtService sets subject = user id).
   *
   * This only DECODES the payload — it does not verify the signature, and must never be
   * trusted for authorisation. That check belongs on the server, which validates the token
   * on every request. Here it is used purely to build a URL.
   */
  getUserId(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      // JWTs use base64url; atob expects standard base64.
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json).sub ?? null;
    } catch {
      return null;
    }
  }
}