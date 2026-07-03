package org.example.productservice.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Enables Spring's cache abstraction, backed by Caffeine (in-memory).
 *
 * A cache MUST have bounds, otherwise it leaks memory and serves stale data forever:
 *   - expireAfterWrite → entries live at most 10 min (staleness ceiling)
 *   - maximumSize       → at most 10k entries, then least-recently-used are evicted
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("products");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000));
        return manager;
    }
}
