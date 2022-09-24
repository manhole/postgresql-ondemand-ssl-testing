package example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class PostgreSQL13Container extends PostgreSQLContainer<PostgreSQL13Container> {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQL13Container.class);

    public PostgreSQL13Container() {
        super("postgres:13-alpine");
    }

    @Override
    protected void configure() {
        super.configure();
        withLogConsumer(new Slf4jLogConsumer(logger));
    }

    public Integer getPort() {
        return super.getMappedPort(POSTGRESQL_PORT);
    }

}
