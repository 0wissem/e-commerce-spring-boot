import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { GetProductUseCase } from '../../application/get-product.usecase';
import { Product } from '../../domain/product.model';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    @if (loading) {
      <div class="max-w-2xl mx-auto px-4 py-12">
        <div class="bg-gray-100 rounded-xl h-64 animate-pulse"></div>
      </div>
    }
    @if (product) {
      <div class="max-w-2xl mx-auto px-4 py-12">
        <div class="bg-white rounded-xl shadow p-6 flex flex-col gap-4">
          <span class="text-xs text-indigo-600 font-medium bg-indigo-50 px-2 py-0.5 rounded w-fit">
            {{ product.categories[0]?.name ?? 'Uncategorized' }}
          </span>
          <h1 class="text-2xl font-bold text-gray-900">{{ product.name }}</h1>
          <div class="flex items-center justify-between pt-4 border-t">
            <div class="flex flex-col">
              <span class="text-3xl font-bold text-gray-900">{{ product.finalPrice | number:'1.2-2' }} $</span>
              @if (product.finalPrice !== product.price) {
                <span class="text-sm text-gray-400 line-through">{{ product.price | number:'1.2-2' }} $</span>
              }
            </div>
            <span class="text-sm" [class]="product.stockQuantity > 0 ? 'text-green-600' : 'text-red-500'">
              {{ product.stockQuantity > 0 ? product.stockQuantity + ' in stock' : 'Out of stock' }}
            </span>
          </div>
        </div>
      </div>
    }
  `
})
export class ProductDetailComponent implements OnInit {
  product: Product | null = null;
  loading = true;

  constructor(private route: ActivatedRoute, private getProduct: GetProductUseCase) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.getProduct.execute(id).subscribe({
      next: p => { this.product = p; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }
}