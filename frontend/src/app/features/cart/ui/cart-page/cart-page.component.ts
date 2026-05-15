import { Component } from '@angular/core';
import { AsyncPipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CartService } from '../../application/cart.service';
import { CartItem } from '../../domain/cart.model';
import { ToastService } from '../../../../shared/services/toast.service';

@Component({
  selector: 'app-cart-page',
  standalone: true,
  imports: [AsyncPipe, DecimalPipe, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-8">
      <h1 class="text-2xl font-bold text-slate-900 mb-6">Shopping Cart</h1>

      @if ((cart.cart$ | async)?.items?.length === 0) {
        <div class="bg-white rounded-2xl border border-slate-100 p-12 text-center" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <div class="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-4 bg-orange-50">
            <svg class="w-7 h-7 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
            </svg>
          </div>
          <p class="text-slate-800 font-semibold mb-1">Your cart is empty</p>
          <p class="text-slate-400 text-sm mb-4">Add some products to get started</p>
          <a routerLink="/products" class="inline-block text-sm font-semibold text-white px-5 py-2 rounded-lg transition" style="background:#ea6500">Browse Products</a>
        </div>
      } @else {
        <div class="bg-white rounded-2xl border border-slate-100 overflow-hidden mb-4" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          @for (item of (cart.cart$ | async)?.items; track item.productId) {
            <div class="flex items-center gap-4 px-5 py-4 border-b border-slate-50 last:border-0">
              <div class="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center shrink-0">
                <svg class="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10"/>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-slate-900 text-sm truncate">{{ item.productName }}</p>
                <p class="text-xs text-slate-400 mt-0.5">{{ item.unitPrice | number:'1.2-2' }} $ each</p>
              </div>
              <div class="flex items-center gap-2">
                <button (click)="cart.updateQuantity(item.productId, item.quantity - 1)"
                  class="w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-600 transition text-sm font-bold">−</button>
                <span class="w-6 text-center text-sm font-semibold text-slate-800">{{ item.quantity }}</span>
                <button (click)="cart.updateQuantity(item.productId, item.quantity + 1)"
                  class="w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-600 transition text-sm font-bold">+</button>
              </div>
              <p class="w-20 text-right font-semibold text-slate-900 text-sm">{{ item.unitPrice * item.quantity | number:'1.2-2' }} $</p>
              <button (click)="remove(item)" class="text-slate-300 hover:text-red-400 transition ml-1">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>
          }
        </div>

        <div class="bg-white rounded-2xl border border-slate-100 p-5" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <div class="flex items-center justify-between mb-4">
            <span class="text-slate-500 text-sm">Total</span>
            <span class="text-xl font-bold text-slate-900">{{ cart.total$ | async | number:'1.2-2' }} $</span>
          </div>
          <div class="flex gap-3">
            <button (click)="clearCart()" class="flex-1 py-2.5 rounded-xl border border-slate-200 text-sm font-medium text-slate-600 hover:bg-slate-50 transition">Clear cart</button>
            <button (click)="checkout()" class="flex-1 py-2.5 rounded-xl text-sm font-semibold text-white transition" style="background:#ea6500">Checkout</button>
          </div>
        </div>
      }
    </div>
  `
})
export class CartPageComponent {
  constructor(public cart: CartService, private toast: ToastService) {}

  remove(item: CartItem) {
    this.cart.removeItem(item.productId);
    this.toast.info(`${item.productName} removed from cart`);
  }

  clearCart() {
    this.cart.clear();
    this.toast.info('Cart cleared');
  }

  checkout() {
    this.toast.info('Checkout coming soon');
  }
}