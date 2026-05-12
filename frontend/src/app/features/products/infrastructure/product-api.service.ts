import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IProductRepository } from '../domain/product.repository';
import { Product, ProductPage, ProductSearchParams } from '../domain/product.model';

@Injectable({ providedIn: 'root' })
export class ProductApiService implements IProductRepository {
  private readonly base = `${environment.apiUrl}/api/products`;

  constructor(private http: HttpClient) {}

  getAll(page: number, size: number): Observable<ProductPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(`${this.base}`, { params }).pipe(map(r => r.data));
  }

  search(p: ProductSearchParams): Observable<ProductPage> {
    let params = new HttpParams().set('page', p.page).set('size', p.size);
    if (p.query)    params = params.set('query', p.query);
    if (p.minPrice != null) params = params.set('minPrice', p.minPrice);
    if (p.maxPrice != null) params = params.set('maxPrice', p.maxPrice);
    if (p.inStock != null)  params = params.set('inStock', p.inStock);
    return this.http.get<any>(`${this.base}/search`, { params }).pipe(map(r => r.data));
  }

  getById(id: string): Observable<Product> {
    return this.http.get<any>(`${this.base}/${id}`).pipe(map(r => r.data));
  }
}