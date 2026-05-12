import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { IProductRepository } from '../domain/product.repository';
import { ProductPage, ProductSearchParams } from '../domain/product.model';
import { ProductApiService } from '../infrastructure/product-api.service';

@Injectable({ providedIn: 'root' })
export class SearchProductsUseCase {
  constructor(private repo: ProductApiService) {}

  execute(params: ProductSearchParams): Observable<ProductPage> {
    if (params.query || params.minPrice != null || params.maxPrice != null || params.inStock != null) {
      return this.repo.search(params);
    }
    return this.repo.getAll(params.page, params.size);
  }
}