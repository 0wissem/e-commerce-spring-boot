import { Component } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ToastService, Toast } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [AsyncPipe],
  template: `
    <div class="fixed bottom-5 right-5 z-50 flex flex-col gap-2">
      @for (toast of toastService.toasts$ | async; track toast.id) {
        <div
          class="flex items-center gap-3 px-4 py-3 rounded-xl shadow-lg text-sm font-medium text-white min-w-64 max-w-sm"
          [class]="bgClass(toast)"
        >
          <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            @if (toast.type === 'success') {
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/>
            } @else if (toast.type === 'error') {
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12"/>
            } @else {
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
            }
          </svg>
          <span class="flex-1">{{ toast.message }}</span>
          <button (click)="toastService.dismiss(toast.id)" class="opacity-70 hover:opacity-100 transition">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
      }
    </div>
  `
})
export class ToastComponent {
  constructor(public toastService: ToastService) {}

  bgClass(toast: Toast): string {
    if (toast.type === 'success') return 'bg-green-600';
    if (toast.type === 'error') return 'bg-red-600';
    return 'bg-slate-700';
  }
}