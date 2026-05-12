import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthUseCase } from '../../application/auth.usecase';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-slate-50 py-12 px-4">
      <div class="w-full max-w-sm">
        <div class="text-center mb-8">
          <div class="w-12 h-12 rounded-2xl flex items-center justify-center mx-auto mb-4" style="background:#ea6500;box-shadow:0 4px 12px rgba(234,101,0,0.3)">
            <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
            </svg>
          </div>
          <h1 class="text-2xl font-bold text-slate-900">Welcome back</h1>
          <p class="text-sm text-slate-500 mt-1">Sign in to your ShopApp account</p>
        </div>

        <div class="bg-white rounded-2xl border border-slate-100 p-6 flex flex-col gap-4" style="box-shadow:0 4px 12px rgba(0,0,0,0.08)">
          <div>
            <label class="text-xs font-medium text-slate-600 mb-1.5 block">Email address</label>
            <input [(ngModel)]="email" type="email" placeholder="you@example.com"
                   class="w-full border border-slate-200 rounded-xl px-4 py-2.5 text-sm bg-slate-50 focus:bg-white focus:outline-none focus:border-orange-400 transition" />
          </div>
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <label class="text-xs font-medium text-slate-600">Password</label>
              <a href="#" class="text-xs font-medium" style="color:#ea6500">Forgot password?</a>
            </div>
            <input [(ngModel)]="password" type="password" placeholder="••••••••"
                   class="w-full border border-slate-200 rounded-xl px-4 py-2.5 text-sm bg-slate-50 focus:bg-white focus:outline-none focus:border-orange-400 transition" />
          </div>
          @if (error) {
            <p class="text-xs text-red-500 bg-red-50 px-3 py-2 rounded-lg">{{ error }}</p>
          }
          <button (click)="submit()" [disabled]="loading"
                  class="w-full text-white font-semibold py-2.5 rounded-xl text-sm transition disabled:opacity-50 mt-1"
                  style="background:#ea6500">
            {{ loading ? 'Signing in...' : 'Sign in' }}
          </button>
        </div>

        <p class="text-center text-sm text-slate-500 mt-5">
          Don't have an account?
          <a routerLink="/auth/register" class="font-semibold" style="color:#ea6500">Create one</a>
        </p>
      </div>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  loading = false;
  error = '';

  constructor(private auth: AuthUseCase) {}

  submit() {
    this.loading = true;
    this.error = '';
    this.auth.login({ email: this.email, password: this.password }).subscribe({
      error: () => { this.error = 'Invalid credentials'; this.loading = false; }
    });
  }
}