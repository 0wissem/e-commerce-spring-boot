package org.example.productservice.product.application;

import org.example.productservice.product.application.dto.ProductRequest;
import org.example.productservice.product.application.dto.ProductResponse;
import org.example.productservice.product.application.dto.ProductSearchRequest;
import org.example.productservice.shared.response.PageResponse;

import java.util.List;

public interface IProductService {
    List<ProductResponse> getAll();
    PageResponse<ProductResponse> getAll(int page, int size);
    ProductResponse getById(String id);
    ProductResponse getByName(String name);
    ProductResponse create(ProductRequest request);
    ProductResponse update(String id, ProductRequest request);
    void delete(String id);

    /**
     * Removes stock in its own transaction.
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException if a concurrent
     *         transaction changed the row first — the caller is expected to retry.
     *         See {@link StockService}, which owns that retry loop.
     */
    ProductResponse decrementStock(String id, int quantity);

    /**
     * Same as {@link #decrementStock}, but locks the row on read (SELECT ... FOR UPDATE).
     * Callers serialise instead of colliding, so this never needs a retry.
     */
    ProductResponse decrementStockPessimistic(String id, int quantity);

    /** Puts units back — the compensating action when an order fails partway through. */
    ProductResponse incrementStock(String id, int quantity);
    PageResponse<ProductResponse> search(ProductSearchRequest request);
}