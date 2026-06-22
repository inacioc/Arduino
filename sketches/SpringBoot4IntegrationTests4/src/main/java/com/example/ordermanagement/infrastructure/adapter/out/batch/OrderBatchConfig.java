package com.example.ordermanagement.infrastructure.adapter.out.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job that applies status changes to orders from a CSV file.
 * <p>
 * Launched from the command line via the main Spring Boot application (see
 * {@link OrderStatusBatchRunner}) with two arguments:
 * <pre>
 *   java -jar order-management.jar --inputFile=orders-in.csv --outputFile=orders-out.csv
 * </pre>
 * <ul>
 *   <li><b>input</b>  CSV columns: {@code orderId,targetStatus} (with header row)</li>
 *   <li><b>output</b> CSV columns: {@code id,presentStatus,result} (with header row),
 *       where {@code result} is {@code OK} or an error code.</li>
 * </ul>
 * The file paths arrive as job parameters, so the reader/writer are {@code @StepScope}.
 */
@Configuration
public class OrderBatchConfig {

    public static final String JOB_NAME  = "updateOrderStatusJob";
    public static final String STEP_NAME = "updateOrderStatusStep";

    private static final int CHUNK_SIZE = 10;

    @Bean
    public Job updateOrderStatusJob(JobRepository jobRepository, Step updateOrderStatusStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(updateOrderStatusStep)
                .build();
    }

    @Bean
    public Step updateOrderStatusStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            FlatFileItemReader<OrderStatusChangeRequest> statusChangeReader,
            ItemProcessor<OrderStatusChangeRequest, OrderStatusChangeResult> statusChangeProcessor,
            FlatFileItemWriter<OrderStatusChangeResult> statusChangeWriter) {

        return new StepBuilder(STEP_NAME, jobRepository)
                .<OrderStatusChangeRequest, OrderStatusChangeResult>chunk(CHUNK_SIZE, txManager)
                .reader(statusChangeReader)
                .processor(statusChangeProcessor)
                .writer(statusChangeWriter)
                .build();
    }

    // ── Reader: input CSV (orderId,targetStatus) ──────────────────────────────

    @Bean
    @StepScope
    public FlatFileItemReader<OrderStatusChangeRequest> statusChangeReader(
            @Value("#{jobParameters['inputFile']}") String inputFile) {

        return new FlatFileItemReaderBuilder<OrderStatusChangeRequest>()
                .name("statusChangeReader")
                .resource(new FileSystemResource(inputFile))
                .linesToSkip(1) // header row
                .delimited()
                .names("orderId", "targetStatus")
                .fieldSetMapper(fs -> new OrderStatusChangeRequest(
                        fs.readString("orderId"),
                        fs.readString("targetStatus")))
                .build();
    }

    // ── Writer: output CSV (id,presentStatus,result) ──────────────────────────

    @Bean
    @StepScope
    public FlatFileItemWriter<OrderStatusChangeResult> statusChangeWriter(
            @Value("#{jobParameters['outputFile']}") String outputFile) {

        return new FlatFileItemWriterBuilder<OrderStatusChangeResult>()
                .name("statusChangeWriter")
                .resource(new FileSystemResource(outputFile))
                .headerCallback(writer -> writer.write("id,presentStatus,result"))
                .lineAggregator(r -> String.join(",",
                        r.id(), r.presentStatus(), r.result()))
                .build();
    }
}
