package org.example.springboot0.shared.backfill;

import org.example.springboot0.order.domain.IOrderRepository;
import org.example.springboot0.order.domain.Order;
import org.example.springboot0.order.domain.OrderProductSnapshot;
import org.example.springboot0.product.domain.IProductRepository;
import org.example.springboot0.product.domain.Product;
import org.example.springboot0.shared.event.CategoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OrderBackfillProcessor {

    private final IOrderRepository orderRepository;
    private final IProductRepository productRepository;

    public OrderBackfillProcessor(IOrderRepository orderRepository, IProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public boolean processPage(int page, int pageSize, int[] totals) {
        Page<Order> batch = orderRepository.findAll(PageRequest.of(page, pageSize));
        for (Order order : batch.getContent()) {
            for (var item : order.getItems()) {
                var productOpt = productRepository.findById(item.getProductId());
                if (productOpt.isEmpty()) {
                    totals[1]++;
                    continue;
                }
                Product product = productOpt.get();
                List<CategoryDto> categories = product.getCategories().stream()
                        .map(c -> new CategoryDto(c.getId(), c.getName()))
                        .toList();
                item.setProductSnapshot(new OrderProductSnapshot(product.getName(), product.getPrice(), categories));
                totals[0]++;
            }
            orderRepository.save(order);
        }
        return batch.hasNext();
    }
}
