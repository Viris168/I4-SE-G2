package edu.itc.shop.support;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

// Challenge: switches the SQL/bind/batch and transaction loggers off for a measurement and restores them
// afterwards (OptimisticLockingTest's application-test.yml turns transaction logging up to DEBUG/TRACE).
// Log levels are global to the JVM, and a test context started later re-applies application.yml,
// so @SpringBootTest(properties = "logging.level...=OFF") does not hold for a cached context.
@Component
public class QuietSql {

    private static final String[] LOGGERS = {
            "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.hibernate.orm.jdbc.batch",
            "org.springframework.orm.jpa", "org.springframework.transaction"};

    private final LoggingSystem logging;

    public QuietSql(LoggingSystem logging) {
        this.logging = logging;
    }

    public interface Muted extends AutoCloseable {
        @Override
        void close();
    }

    public Muted mute() {
        Map<String, LogLevel> before = new HashMap<>();
        for (String name : LOGGERS) {
            var config = logging.getLoggerConfiguration(name);
            before.put(name, config == null ? null : config.getConfiguredLevel());
            logging.setLogLevel(name, LogLevel.OFF);
        }
        return () -> before.forEach(logging::setLogLevel);
    }
}
