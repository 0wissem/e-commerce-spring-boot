import { Component, OnInit } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { GetOrdersUseCase } from '../../application/get-orders.usecase';
import { Order, OrderStatus } from '../../domain/order.model';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-slate-900 mb-6">My Orders</h1>

      @if (loading && orders.length === 0) {
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
                  <p class="text-xs text-slate-400 mb-0.5 font-mono">{{ order.orderNumber }}</p>
                  <p class="font-semibold text-slate-900 text-sm">
                    {{ order.items.length }} item{{ order.items.length !== 1 ? 's' : '' }}
                    · {{ order.totalPrice | currency:(order.currency || 'EUR'):'symbol':'1.2-2' }}
                  </p>
                  @if (order.createdAt) {
                    <p class="text-xs text-slate-400 mt-0.5">{{ order.createdAt | date:'medium' }}</p>
                  }
                </div>
                <span class="text-xs font-semibold px-3 py-1 rounded-full" [class]="statusClass(order.status)">
                  {{ order.status }}
                </span>
              </div>

              <div class="px-5 py-3 flex flex-col gap-1">
                @for (item of order.items; track item.id) {
                  <div class="flex items-center justify-between text-sm">
                    <span class="text-slate-600">
                      {{ item.productName }}
                      @if (item.productSnapshot?.brand) {
                        <span class="text-xs text-slate-400">· {{ item.productSnapshot?.brand }}</span>
                      }
                    </span>
                    <span class="text-slate-400 text-xs">
                      × {{ item.quantity }} · {{ item.subtotal | currency:(order.currency || 'EUR'):'symbol':'1.2-2' }}
                    </span>
                  </div>
                }
              </div>

              @if (order.shippingAddress?.city) {
                <div class="px-5 pb-3 text-xs text-slate-400">
                  Shipped to {{ order.shippingAddress?.city }}, {{ order.shippingAddress?.country }}
                </div>
              }
            </div>
          }
        </div>

        <!--
          Keyset pagination gives next-only: the cursor is a POSITION, not an offset, so
          there is no "jump to page N" and no total count. "Load more" is the honest UI.
        -->
        @if (hasMore) {
          <div class="flex justify-center mt-6">
            <button (click)="loadMore()" [disabled]="loading"
              class="px-5 py-2 text-sm font-medium rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition">
              {{ loading ? 'Loading…' : 'Load more' }}
            </button>
          </div>
        }
      }
    </div>
  `
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  hasMore = false;

  private nextCursor: string | null = null;

  constructor(private getOrders: GetOrdersUseCase) {}

  ngOnInit() { this.load(); }

  loadMore() { this.load(); }

  private load() {
    this.loading = true;
    this.getOrders.history(this.nextCursor).subscribe({
      next: page => {
        // Append: each page continues from the cursor rather than replacing the list.
        this.orders = [...this.orders, ...page.items];
        this.nextCursor = page.nextCursor;
        this.hasMore = page.hasMore;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

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
