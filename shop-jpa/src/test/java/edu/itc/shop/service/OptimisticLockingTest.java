package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class OptimisticLockingTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private TestData data;

    @Autowired
    private FindByIdBarrier findByIdBarrier;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        findByIdBarrier.clear();
        createdOrderIds.forEach(data::deleteOrder);
        createdOrderIds.clear();
    }

    @Test
    void secondWriterFailsWithOptimisticLock() throws Exception {
        Long orderId = newTrackedOrder();
        long versionBefore = orders.findById(orderId).orElseThrow().getVersion();

        CountDownLatch bothLoaded = new CountDownLatch(2);
        AtomicReference<OrderStatus> winnerStatus = new AtomicReference<>();

        Function<OrderStatus, Callable<Throwable>> writer = target -> () -> {
            try {
                tx.executeWithoutResult(status -> {
                    Order o = orders.findById(orderId).orElseThrow();
                    bothLoaded.countDown();
                    await(bothLoaded);
                    o.setStatus(target);
                });
                winnerStatus.set(target);
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> results = pool.invokeAll(
                    List.of(writer.apply(OrderStatus.PAID), writer.apply(OrderStatus.CANCELLED)));
            List<Throwable> failures = results.stream().map(OptimisticLockingTest::get).filter(Objects::nonNull).toList();

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

            Order after = orders.findById(orderId).orElseThrow();
            assertThat(after.getVersion()).isEqualTo(versionBefore + 1);
            assertThat(after.getStatus()).isEqualTo(winnerStatus.get());
        }
    }

    @Test
    void facadeRetriesAndBothWritersEventuallySucceed() throws Exception {
        Long orderId = newTrackedOrder();
        long versionBefore = orders.findById(orderId).orElseThrow().getVersion();

        CountDownLatch bothLoaded = new CountDownLatch(2);
        AtomicInteger loadCount = new AtomicInteger();

        // Force the first attempt of both concurrent transactions to overlap on the same version.
        findByIdBarrier.onFindById(id -> {
            if (orderId.equals(id) && loadCount.incrementAndGet() <= 2) {
                bothLoaded.countDown();
                await(bothLoaded);
            }
        });

        Function<OrderStatus, Callable<Throwable>> writer = target -> () -> {
            try {
                orderFacade.changeStatusWithRetry(orderId, target);
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> results = pool.invokeAll(
                    List.of(writer.apply(OrderStatus.PAID), writer.apply(OrderStatus.CANCELLED)));
            List<Throwable> failures = results.stream().map(OptimisticLockingTest::get).filter(Objects::nonNull).toList();

            assertThat(failures).isEmpty();
            assertThat(loadCount.get()).isEqualTo(3);

            Order after = orders.findById(orderId).orElseThrow();
            assertThat(after.getVersion()).isEqualTo(versionBefore + 2);
            assertThat(after.getStatus()).isIn(OrderStatus.PAID, OrderStatus.CANCELLED);
        }
    }

    private Long newTrackedOrder() {
        Long id = data.newOrder();
        createdOrderIds.add(id);
        return id;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Throwable get(Future<Throwable> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return e;
        }
    }

    static class FindByIdBarrier {
        private final AtomicReference<Consumer<Object>> hook = new AtomicReference<>();

        void onFindById(Consumer<Object> consumer) {
            hook.set(consumer);
        }

        void clear() {
            hook.set(null);
        }

        void afterFindById(Object id) {
            Consumer<Object> consumer = hook.get();
            if (consumer != null) {
                consumer.accept(id);
            }
        }
    }

    @TestConfiguration
    static class BarrierConfig {

        @Bean
        FindByIdBarrier findByIdBarrier() {
            return new FindByIdBarrier();
        }

        @Bean
        static BeanPostProcessor orderRepositoryBarrier(FindByIdBarrier barrier) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!(bean instanceof OrderRepository repo)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            OrderRepository.class.getClassLoader(),
                            bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(repo, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getTargetException();
                                }
                                if ("findById".equals(method.getName()) && args != null && args.length == 1) {
                                    barrier.afterFindById(args[0]);
                                }
                                return result;
                            });
                }
            };
        }
    }
}
