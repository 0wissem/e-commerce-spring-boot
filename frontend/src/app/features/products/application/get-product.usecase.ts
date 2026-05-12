import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Product } from '../domain/product.model';
import { ProductApiService } from '../infrastructure/product-api.service';

@Injectable({ providedIn: 'root' })
export class GetProductUseCase {
  constructor(private repo: ProductApiService) {}

  execute(id: string): Observable<Product> {
    return this.repo.getById(id);
  }
}