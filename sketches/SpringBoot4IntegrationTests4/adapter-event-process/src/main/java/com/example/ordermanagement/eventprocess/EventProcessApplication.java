package com.example.ordermanagement.eventprocess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Executable, standalone consumer of Spring Modulith's event-publication table.
 * <p>
 * Lives in its own package (not the shared root, not the adapter's own package) so it
 * does not collide with another application's {@code @SpringBootApplication} when that
 * application depends on this module just to embed its beans instead of running this
 * class — same reasoning as {@code MessagingApplication}. Component scanning is limited
 * to that one adapter package; nothing else in this module needs to be scanned to run
 * standalone.
 * <p>
 * Boots both consumption styles this module offers, but only one is actually useful
 * here: {@code ModulithEventLogger}'s DB polling works fine as a genuinely separate
 * process. {@code ApplicationEventLogger}'s {@code @ApplicationModuleListener} only
 * fires for events published in the SAME process — and nothing ever publishes one in
 * this standalone app — so it stays present (for consistency, and in case something is
 * ever published here directly) but is effectively inert. Embed this module into the
 * actual publishing application instead if the listener-based path is what you need.
 * <p>
 * {@code @EnableScheduling} lives here, not on {@code ModulithEventLogger} itself,
 * precisely so embedding the adapter elsewhere doesn't silently turn a host app that
 * must stay a one-shot process into a long-running one — see that class's javadoc.
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.ordermanagement.infrastructure.adapter.eventprocess"
})
@EnableScheduling
public class EventProcessApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventProcessApplication.class, args);
    }
}
