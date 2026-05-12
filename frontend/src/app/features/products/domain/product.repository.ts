import { Observable } from 'rxjs';
import { Product, ProductPage, ProductSearchParams } from './product.model';

export abstract class IProductRepository {
  abstract getAll(page: number, size: number): Observable<ProductPage>;
  abstract search(params: ProductSearchParams): Observable<ProductPage>;
  abstract getById(id: string): Observable<Product>;
}