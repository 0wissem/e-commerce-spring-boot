import { Component } from '@angular/core';

@Component({
  selector: 'app-order-list',
  standalone: true,
  template: `
    <div class="max-w-4xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-slate-900 mb-6">My Orders</h1>
      <div class="bg-white rounded-2xl border border-slate-100 p-8 text-center" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
        <div class="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-4 bg-orange-50">
          <svg class="w-7 h-7 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
          </svg>
        </div>
        <p class="text-slate-800 font-semibold mb-1">Orders coming soon</p>
        <p class="text-slate-400 text-sm">Available once JWT authentication is implemented.</p>
      </div>
    </div>
  `
})
export class OrderListComponent {}