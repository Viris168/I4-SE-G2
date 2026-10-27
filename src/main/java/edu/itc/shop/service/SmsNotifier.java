package edu.itc.shop.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmsNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    @PostConstruct
    public void init() {
        log.info("SmsNotifier ready");
    }

    @PreDestroy
    public void destroy() {
        log.info("SmsNotifier closing");
    }

    @Override
    public void send(String to, String text) {
        log.info("SMS to={} text={}", to, text);
    }
}
