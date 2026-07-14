package com.hackathon.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventSchemaUpdater {

    private static final Logger log = LoggerFactory.getLogger(EventSchemaUpdater.class);

    private static final String DROP_CONSTRAINT_SQL =
            "alter table if exists events drop constraint if exists events_status_check";
    private static final String NORMALIZE_OLD_VALUES_SQL =
            "update events set status = case "
                    + "when status = 'DRAFT' then 'UPCOMING' "
                    + "when status = 'COMPLETED' then 'CLOSED' "
                    + "else status end "
                    + "where status in ('DRAFT', 'COMPLETED')";
    private static final String ADD_CONSTRAINT_SQL =
            "alter table if exists events add constraint events_status_check "
                + "check (status is null or status in ('UPCOMING', 'OPEN', 'CLOSED', 'CANCELLED'))";

    private final JdbcTemplate jdbcTemplate;

    public EventSchemaUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void alignEventStatusConstraint() {
        log.info("SchemaUpdate phase=start table=events constraint=events_status_check");
        jdbcTemplate.execute(DROP_CONSTRAINT_SQL);
        jdbcTemplate.execute(NORMALIZE_OLD_VALUES_SQL);
        jdbcTemplate.execute(ADD_CONSTRAINT_SQL);
        log.info("SchemaUpdate phase=done table=events constraint=events_status_check");
    }
}
