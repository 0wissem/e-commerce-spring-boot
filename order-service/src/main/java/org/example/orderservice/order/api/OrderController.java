package org.example.orderservice.order.api;

import jakarta.validation.Valid;
import org.example.orderservice.order.application.IOrderService;
import org.example.orderservice.order.application.dto.OrderRequest;
import org.example.orderservice.order.application.dto.OrderResponse;
import org.example.orderservice.order.application.dto.OrderStatusRequest;
import org.example.orderservice.shared.response.ApiResponse;
import org.example.orderservice.shared.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final IOrderService orderService;

    public OrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAll(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByCustomerId(@PathVariable String customerId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getByCustomerId(customerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created", orderService.create(request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable String id,
                                                                    @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order status updated", orderService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        orderService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Order deleted", null));
    }
}
