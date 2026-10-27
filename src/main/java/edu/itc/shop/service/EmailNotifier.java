package edu.itc.shop.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev")
@Primary
public class EmailNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    @PostConstruct
    public void init() {
        log.info("EmailNotifier ready");
    }

    @PreDestroy
    public void destroy() {
        log.info("EmailNotifier closing");
    }

    @Override
    public void send(String to, String text) {
        log.info("EMAIL to={} text={}", to, text);
    }
}
