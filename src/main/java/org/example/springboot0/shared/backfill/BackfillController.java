package org.example.springboot0.shared.backfill;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/backfill")
public class BackfillController {

    private final BackfillService backfillService;

    public BackfillController(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> backfillProducts() {
        backfillService.runProductBackfill();
        return ResponseEntity.accepted().body(Map.of("status", "product backfill started"));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> backfillOrders() {
        backfillService.runOrderBackfill();
        return ResponseEntity.accepted().body(Map.of("status", "order backfill started"));
    }
}
