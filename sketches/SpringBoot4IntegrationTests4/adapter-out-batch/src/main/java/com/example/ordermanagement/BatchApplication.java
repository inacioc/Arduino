package com.example.ordermanagement;

import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.service.ChangeOrderStatusService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Executable batch runner.
 * <p>
 * Component scanning is limited to the batch and persistence adapters. The batch job
 * drives the domain through the {@link ChangeOrderStatusUseCase} inbound port, but that
 * port's implementation ({@link ChangeOrderStatusService}) lives in {@code domain.service}
 * — a package we deliberately do <em>not</em> scan, because it also holds
 * {@code OrderDomainService}, whose event/product ports have no adapter on this app's
 * classpath. Instead we register the one service the batch needs explicitly (it depends
 * only on {@link OrderRepositoryPort}), keeping the app's footprint to domain +
 * persistence. JPA entity/repository scanning still defaults to this root package.
 * <p>
 * Launch with: {@code --inputFile=orders-in.csv --outputFile=orders-out.csv}
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.ordermanagement.infrastructure.adapter.out.batch",
        "com.example.ordermanagement.infrastructure.adapter.out.persistence"
})
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }

    /** The single inbound use case the batch job drives, wired to the repository port. */
    @Bean
    public ChangeOrderStatusUseCase changeOrderStatusUseCase(OrderRepositoryPort orderRepository) {
        return new ChangeOrderStatusService(orderRepository);
    }
}
