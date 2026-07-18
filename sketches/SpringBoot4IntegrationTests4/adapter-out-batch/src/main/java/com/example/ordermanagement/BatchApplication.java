package com.example.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Executable batch runner.
 * <p>
 * Component scanning is limited to the batch and persistence adapters — the
 * domain {@code @Service} beans are not needed here (the job talks straight to
 * {@code OrderRepositoryPort}), so scanning them would pull in unsatisfied
 * outbound ports (e.g. the event port). JPA entity/repository scanning still
 * defaults to this root package, finding the persistence adapter.
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
}
