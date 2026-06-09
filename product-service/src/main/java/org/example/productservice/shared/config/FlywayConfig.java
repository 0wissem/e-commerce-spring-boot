package org.example.productservice.shared.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations,
            // Gated, off by default. Set FLYWAY_CLEAN_ON_START=true on an environment
            // to wipe and rebuild its schema on the next boot (preprod only — NEVER prod).
            @Value("${FLYWAY_CLEAN_ON_START:false}") boolean cleanOnStart) {

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .cleanDisabled(false)
                .load();

        if (cleanOnStart) {
            flyway.clean();
        }
        flyway.migrate();
        return flyway;
    }

    // In Spring Boot 4.x, make the JPA EntityManagerFactory wait for Flyway to finish
    // so the schema exists before Hibernate validates against it.
    @Bean
    public static BeanDefinitionRegistryPostProcessor flywayDependsOnPostProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                for (String beanName : registry.getBeanDefinitionNames()) {
                    BeanDefinition bd = registry.getBeanDefinition(beanName);
                    if ("entityManagerFactory".equals(beanName)) {
                        bd.setDependsOn("flyway");
                    }
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}
        };
    }
}
