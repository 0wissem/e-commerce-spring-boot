import { Component, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { GetOrdersUseCase } from '../../application/get-orders.usecase';
import { Order, OrderStatus } from '../../domain/order.model';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-slate-900 mb-6">My Orders</h1>

      @if (loading) {
        <div class="flex flex-col gap-3">
          @for (i of [1,2,3]; track i) {
            <div class="bg-white rounded-2xl border border-slate-100 p-5 animate-pulse" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
              <div class="flex items-center justify-between mb-3">
                <div class="h-4 bg-slate-100 rounded w-32"></div>
                <div class="h-5 bg-slate-100 rounded-full w-20"></div>
              </div>
              <div class="h-3 bg-slate-100 rounded w-48 mb-1"></div>
              <div class="h-3 bg-slate-100 rounded w-24"></div>
            </div>
          }
        </div>
      } @else if (orders.length === 0) {
        <div class="bg-white rounded-2xl border border-slate-100 p-12 text-center" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <div class="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-4 bg-orange-50">
            <svg class="w-7 h-7 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
            </svg>
          </div>
          <p class="text-slate-800 font-semibold mb-1">No orders yet</p>
          <p class="text-slate-400 text-sm">Your order history will appear here</p>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          @for (order of orders; track order.id) {
            <div class="bg-white rounded-2xl border border-slate-100 overflow-hidden" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
              <div class="flex items-center justify-between px-5 py-4 border-b border-slate-50">
                <div>
                  <p class="text-xs text-slate-400 mb-0.5">Order #{{ order.id.slice(-8).toUpperCase() }}</p>
                  <p class="font-semibold text-slate-900 text-sm">{{ order.items.length }} item{{ order.items.length !== 1 ? 's' : '' }} · {{ order.totalPrice | number:'1.2-2' }} $</p>
                </div>
                <span class="text-xs font-semibold px-3 py-1 rounded-full" [class]="statusClass(order.status)">
                  {{ order.status }}
                </span>
              </div>
              <div class="px-5 py-3 flex flex-col gap-1">
                @for (item of order.items; track item.id) {
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-slate-600">{{ item.productName }}</span>
                    <span class="text-slate-400 text-xs">× {{ item.quantity }} · {{ item.unitPrice * item.quantity | number:'1.2-2' }} $</span>
                  </div>
                }
              </div>
            </div>
          }
        </div>

        @if (totalPages > 1) {
          <div class="flex items-center justify-between mt-6">
            <button (click)="prevPage()" [disabled]="page === 0"
              class="px-4 py-2 text-sm font-medium rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition">Previous</button>
            <span class="text-sm text-slate-400">Page {{ page + 1 }} of {{ totalPages }}</span>
            <button (click)="nextPage()" [disabled]="page >= totalPages - 1"
              class="px-4 py-2 text-sm font-medium rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition">Next</button>
          </div>
        }
      }
    </div>
  `
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  page = 0;
  totalPages = 0;

  constructor(private getOrders: GetOrdersUseCase) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.getOrders.execute(this.page).subscribe({
      next: p => {
        this.orders = p.content;
        this.totalPages = p.totalPages;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  nextPage() { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }
  prevPage() { if (this.page > 0) { this.page--; this.load(); } }

  statusClass(status: OrderStatus): string {
    const map: Record<OrderStatus, string> = {
      PENDING:   'bg-amber-50 text-amber-700',
      CONFIRMED: 'bg-blue-50 text-blue-700',
      SHIPPED:   'bg-indigo-50 text-indigo-700',
      DELIVERED: 'bg-green-50 text-green-700',
      CANCELLED: 'bg-red-50 text-red-600',
    };
    return map[status] ?? 'bg-slate-100 text-slate-600';
  }
}