package tr.ohvps.idempotency.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

import tr.ohvps.idempotency.checksum.ChecksumGenerator;
import tr.ohvps.idempotency.store.IdempotencyStore;
import tr.ohvps.idempotency.store.inmemory.InMemoryIdempotencyStore;
import tr.ohvps.idempotency.web.error.OhvpsErrorWriter;
import tr.ohvps.idempotency.web.filter.IdempotencyFilter;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ChecksumGenerator checksumGenerator(IdempotencyProperties properties) {
        return new ChecksumGenerator(properties.algorithm());
    }

    /**
     * In a multi-node setup this bean should be replaced with a Redis or database backed
     * {@link IdempotencyStore}.
     */
    @Bean
    IdempotencyStore idempotencyStore(Clock clock, IdempotencyProperties properties) {
        return new InMemoryIdempotencyStore(clock, properties.ttl());
    }

    @Bean
    OhvpsErrorWriter ohvpsErrorWriter(Clock clock) {
        return new OhvpsErrorWriter(clock);
    }

    /**
     * The filter must run after authentication/signature verification filters; otherwise an
     * unverified request could obtain a stored response.
     */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyProperties properties,
                                                                ChecksumGenerator checksumGenerator,
                                                                IdempotencyStore store,
                                                                OhvpsErrorWriter errorWriter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(
                new IdempotencyFilter(properties, checksumGenerator, store, errorWriter));
        registration.setName("ohvpsIdempotencyFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
