-- Backing table for Spring Modulith's JPA event-publication registry
-- (org.springframework.modulith.events.jpa.JpaEventPublication), which is what makes
-- @ApplicationModuleListener durable: every publication gets a row here, and it's marked
-- completed once the listener returns without throwing.
--
-- This project owns its schema via Flyway with ddl-auto: validate (Hibernate never
-- auto-creates tables), and spring-modulith-starter-jpa does NOT ship its own migration -
-- unlike the persistence module's own tables, this one has to be created by hand.
--
-- Lives in adapter-events (not adapter-in-web) now that the Modulith event plumbing itself
-- moved here: any app that depends on this module gets this schema contribution too, via
-- Flyway's classpath:db/migration scanning across every jar on the classpath.
--
-- IMPORTANT: this DDL was reconstructed from JpaEventPublication's JPA mapping, not copied
-- from Spring Modulith's official reference docs (no network access when this was written).
-- Verify column names/types against the "Appendix: Database Schema" section of the Spring
-- Modulith reference documentation for the exact version pinned in the root pom
-- (spring-modulith.version) before relying on this against a real database.
CREATE TABLE event_publication (
    id                      UUID PRIMARY KEY,
    listener_id             VARCHAR(512) NOT NULL,
    event_type              VARCHAR(512) NOT NULL,
    serialized_event        TEXT NOT NULL,
    publication_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date         TIMESTAMP WITH TIME ZONE,
    last_resubmission_date  TIMESTAMP WITH TIME ZONE,
    completion_attempts     INTEGER,
    status                  VARCHAR(20)
);

-- The registry's "find incomplete publications" query (what every listener retry and the
-- on-restart republish both run) filters on this.
CREATE INDEX idx_event_publication_incomplete ON event_publication (completion_date)
    WHERE completion_date IS NULL;
