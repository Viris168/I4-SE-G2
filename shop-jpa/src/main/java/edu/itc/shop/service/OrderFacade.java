package edu.itc.shop.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import edu.itc.shop.domain.OrderStatus;

@Service
public class OrderFacade {

    private static final int MAX_ATTEMPTS = 3;

    private final OrderService orderService;

    public OrderFacade(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Retries the whole transaction; must live outside OrderService (the proxy raises at commit). */
    public void changeStatusWithRetry(Long id, OrderStatus next) {
        for (int attempt = 1; ; attempt++) {
            try {
                orderService.changeStatus(id, next);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(20L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during optimistic lock retry backoff", ie);
        }
    }
}
