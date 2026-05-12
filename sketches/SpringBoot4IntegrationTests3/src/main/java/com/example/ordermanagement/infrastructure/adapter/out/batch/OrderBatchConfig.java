package com.example.ordermanagement.infrastructure.adapter.out.batch;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
public class OrderBatchConfig {

    public static final String JOB_NAME  = "processConfirmedOrdersJob";
    public static final String STEP_NAME = "processOrdersStep";

    @Bean
    public Job processConfirmedOrdersJob(JobRepository jobRepository,
                                         Step processOrdersStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(processOrdersStep)
                .build();
    }

    @Bean
    public Step processOrdersStep(JobRepository jobRepository,
                                   PlatformTransactionManager txManager,
                                   OrderRepositoryPort orderRepository,
                                   ItemProcessor<Order, Order> confirmToCompleteProcessor,
                                   ItemWriter<Order> orderItemWriter) {
        // Reader: load all CONFIRMED orders at job start
        ListItemReader<Order> reader = new ListItemReader<>(
                orderRepository.findByStatus(OrderStatus.CONFIRMED)
        );

        return new StepBuilder(STEP_NAME, jobRepository)
                .<Order, Order>chunk(10, txManager)
                .reader(reader)
                .processor(confirmToCompleteProcessor)
                .writer(orderItemWriter)
                .build();
    }

    @Bean
    public ItemProcessor<Order, Order> confirmToCompleteProcessor() {
        return order -> {
            order.startProcessing();
            order.complete();
            return order;
        };
    }

    @Bean
    public ItemWriter<Order> orderItemWriter(OrderRepositoryPort orderRepository) {
        return chunk -> {
            List<? extends Order> items = chunk.getItems();
            items.forEach(orderRepository::save);
        };
    }
}
