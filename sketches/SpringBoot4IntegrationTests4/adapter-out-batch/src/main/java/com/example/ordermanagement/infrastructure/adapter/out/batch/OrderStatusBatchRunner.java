package com.example.ordermanagement.infrastructure.adapter.out.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Launches {@link OrderBatchConfig#JOB_NAME} when the application is started from
 * the command line with both {@code --inputFile} and {@code --outputFile} options:
 * <pre>
 *   java -jar order-management.jar --inputFile=orders-in.csv --outputFile=orders-out.csv
 * </pre>
 * When those options are absent (normal web startup, integration tests) it does
 * nothing, so it never interferes with the running application or {@code @SpringBatchTest},
 * which launch the job explicitly. This is why {@code spring.batch.job.enabled=false}
 * can stay set — we control the launch ourselves rather than relying on Boot's runner.
 */
@Component
public class OrderStatusBatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusBatchRunner.class);

    static final String INPUT_FILE_ARG  = "inputFile";
    static final String OUTPUT_FILE_ARG = "outputFile";

    private final JobLauncher jobLauncher;
    private final Job updateOrderStatusJob;

    public OrderStatusBatchRunner(JobLauncher jobLauncher, Job updateOrderStatusJob) {
        this.jobLauncher          = jobLauncher;
        this.updateOrderStatusJob = updateOrderStatusJob;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String inputFile  = firstValue(args, INPUT_FILE_ARG);
        String outputFile = firstValue(args, OUTPUT_FILE_ARG);

        if (inputFile == null || outputFile == null) {
            log.debug("No --{}/--{} provided; skipping order status batch job.",
                    INPUT_FILE_ARG, OUTPUT_FILE_ARG);
            return;
        }

        JobParameters params = new JobParametersBuilder()
                .addString(INPUT_FILE_ARG, inputFile)
                .addString(OUTPUT_FILE_ARG, outputFile)
                .addLong("timestamp", System.currentTimeMillis()) // make each run unique
                .toJobParameters();

        log.info("Launching {} with input={} output={}",
                OrderBatchConfig.JOB_NAME, inputFile, outputFile);

        JobExecution execution = jobLauncher.run(updateOrderStatusJob, params);
        log.info("Job {} finished with status {}", OrderBatchConfig.JOB_NAME, execution.getStatus());
    }

    private static String firstValue(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
