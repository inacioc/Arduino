package com.example.ordermanagement.infrastructure.adapter.out.batch;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates {@code @Scheduled} processing only under the {@code scheduler} profile.
 * <p>
 * {@link OrderConfirmationRequestPoller}'s {@code @Scheduled} method is otherwise inert
 * (no {@code ScheduledAnnotationBeanPostProcessor} without {@code @EnableScheduling}),
 * but this is also what keeps the default, file-driven CLI mode a genuine one-shot run:
 * Spring's task scheduler holds non-daemon threads open, which would otherwise stop the
 * app ever exiting after {@link OrderStatusBatchRunner} finishes.
 * <p>
 * Run in scheduler mode with: {@code --spring.profiles.active=scheduler}
 */
@Configuration
@Profile("scheduler")
@EnableScheduling
public class SchedulingConfig {
}
