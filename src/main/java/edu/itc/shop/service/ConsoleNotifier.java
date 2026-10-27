package edu.itc.shop.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@Primary
public class ConsoleNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    @PostConstruct
    public void init() {
        log.info("ConsoleNotifier ready");
    }

    @PreDestroy
    public void destroy() {
        log.info("ConsoleNotifier closing");
    }

    @Override
    public void send(String to, String text) {
        log.info("CONSOLE to={} text={}", to, text);
    }
}
