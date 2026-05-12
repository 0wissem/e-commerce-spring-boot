import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ProductCardComponent } from '../product-card/product-card.component';
import { SearchProductsUseCase } from '../../application/search-products.usecase';
import { Product, ProductSearchParams } from '../../domain/product.model';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [FormsModule, ProductCardComponent, DecimalPipe],
  templateUrl: './product-list.component.html'
})
export class ProductListComponent implements OnInit {
  products: Product[] = [];
  totalElements = 0;
  totalPages = 0;
  loading = false;

  query = '';
  minPrice: number | null = null;
  maxPrice: number | null = null;
  inStock = false;
  page = 0;
  size = 12;

  constructor(private searchProducts: SearchProductsUseCase) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    const params: ProductSearchParams = {
      query: this.query || undefined,
      minPrice: this.minPrice ?? undefined,
      maxPrice: this.maxPrice ?? undefined,
      inStock: this.inStock || undefined,
      page: this.page,
      size: this.size
    };
    this.searchProducts.execute(params).subscribe({
      next: page => {
        this.products = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  search() {
    this.page = 0;
    this.load();
  }

  nextPage() {
    if (this.page < this.totalPages - 1) { this.page++; this.load(); }
  }

  prevPage() {
    if (this.page > 0) { this.page--; this.load(); }
  }
}