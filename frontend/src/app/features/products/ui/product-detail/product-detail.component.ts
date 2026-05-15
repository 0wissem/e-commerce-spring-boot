import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { GetProductUseCase } from '../../application/get-product.usecase';
import { Product } from '../../domain/product.model';
import { CartService } from '../../../cart/application/cart.service';
import { ToastService } from '../../../../shared/services/toast.service';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  template: `
    <div class="max-w-3xl mx-auto px-4 py-8">
      <a routerLink="/products" class="inline-flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-700 transition mb-6">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
        </svg>
        Back to products
      </a>

      @if (loading) {
        <div class="bg-white rounded-2xl border border-slate-100 p-6 animate-pulse" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <div class="flex gap-6">
            <div class="w-48 h-48 rounded-xl bg-slate-100 shrink-0"></div>
            <div class="flex-1 flex flex-col gap-3 pt-2">
              <div class="h-4 bg-slate-100 rounded w-20"></div>
              <div class="h-6 bg-slate-100 rounded w-2/3"></div>
              <div class="h-4 bg-slate-100 rounded w-full"></div>
              <div class="h-4 bg-slate-100 rounded w-4/5"></div>
              <div class="h-8 bg-slate-100 rounded w-32 mt-auto"></div>
            </div>
          </div>
        </div>
      } @else if (product) {
        <div class="bg-white rounded-2xl border border-slate-100 overflow-hidden" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <div class="flex flex-col sm:flex-row gap-0">
            <div class="w-full sm:w-64 shrink-0 bg-slate-50 flex items-center justify-center p-10 border-b sm:border-b-0 sm:border-r border-slate-100">
              <svg class="w-20 h-20 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10"/>
              </svg>
            </div>

            <div class="flex-1 p-6 flex flex-col gap-4">
              <div class="flex flex-wrap gap-1.5">
                @for (cat of product.categories; track cat.id) {
                  <span class="text-xs font-semibold px-2.5 py-0.5 rounded-full text-white" style="background:#ea6500">{{ cat.name }}</span>
                }
                @if (product.categories.length === 0) {
                  <span class="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-500">Uncategorized</span>
                }
              </div>

              <h1 class="text-2xl font-bold text-slate-900 leading-tight">{{ product.name }}</h1>

              <div class="flex items-baseline gap-2">
                <span class="text-3xl font-bold text-slate-900">{{ product.finalPrice | number:'1.2-2' }} $</span>
                @if (product.finalPrice !== product.price) {
                  <span class="text-base text-slate-400 line-through">{{ product.price | number:'1.2-2' }} $</span>
                  <span class="text-xs font-bold text-green-600 bg-green-50 px-2 py-0.5 rounded-full">
                    -{{ discount() }}%
                  </span>
                }
              </div>

              <div class="flex items-center gap-2">
                @if (product.stockQuantity === 0) {
                  <span class="text-xs font-semibold text-red-600 bg-red-50 px-2.5 py-1 rounded-full">Out of stock</span>
                } @else if (product.stockQuantity <= 5) {
                  <span class="text-xs font-semibold text-amber-700 bg-amber-50 px-2.5 py-1 rounded-full">Only {{ product.stockQuantity }} left</span>
                } @else {
                  <span class="text-xs font-semibold text-green-700 bg-green-50 px-2.5 py-1 rounded-full">In stock · {{ product.stockQuantity }} available</span>
                }
              </div>

              <div class="border-t border-slate-100 pt-4 mt-auto flex items-center gap-4">
                <div class="flex items-center rounded-xl border border-slate-200 overflow-hidden">
                  <button (click)="decQty()" class="w-9 h-9 flex items-center justify-center text-slate-500 hover:bg-slate-50 transition font-bold text-lg">−</button>
                  <span class="w-10 text-center text-sm font-semibold text-slate-800">{{ quantity }}</span>
                  <button (click)="incQty()" class="w-9 h-9 flex items-center justify-center text-slate-500 hover:bg-slate-50 transition font-bold text-lg">+</button>
                </div>
                <button
                  (click)="addToCart()"
                  [disabled]="product.stockQuantity === 0"
                  class="flex-1 py-2.5 rounded-xl text-sm font-semibold text-white transition flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
                  style="background:#ea6500">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
                  </svg>
                  Add to cart
                </button>
              </div>
            </div>
          </div>
        </div>
      } @else {
        <div class="bg-white rounded-2xl border border-slate-100 p-12 text-center" style="box-shadow:0 1px 3px rgba(0,0,0,0.07)">
          <p class="text-slate-500">Product not found.</p>
        </div>
      }
    </div>
  `
})
export class ProductDetailComponent implements OnInit {
  product: Product | null = null;
  loading = true;
  quantity = 1;

  constructor(
    private route: ActivatedRoute,
    private getProduct: GetProductUseCase,
    private cart: CartService,
    private toast: ToastService
  ) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.getProduct.execute(id).subscribe({
      next: p => { this.product = p; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  incQty() { if (this.product && this.quantity < this.product.stockQuantity) this.quantity++; }
  decQty() { if (this.quantity > 1) this.quantity--; }

  discount(): number {
    if (!this.product) return 0;
    return Math.round((1 - this.product.finalPrice / this.product.price) * 100);
  }

  addToCart() {
    if (!this.product) return;
    this.cart.addItem({
      productId: this.product.id,
      productName: this.product.name,
      unitPrice: this.product.finalPrice
    }, this.quantity);
    this.toast.success(`${this.product.name} added to cart`);
  }
}