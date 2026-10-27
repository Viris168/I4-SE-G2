package edu.itc.shop.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeServiceTest {

    record Call(String to, String text) {}

    static class FakeNotifier implements Notifier {
        final List<Call> calls = new ArrayList<>();

        @Override
        public void send(String to, String text) {
            calls.add(new Call(to, text));
        }
    }

    @Test
    void welcomeSendsToDefaultNotifierWhenNotUrgent() {
        var defaultNotifier = new FakeNotifier();
        var urgentNotifier = new FakeNotifier();
        var clock = Clock.fixed(Instant.parse("2026-09-25T09:15:02Z"), ZoneId.of("UTC"));

        var service = new WelcomeService(defaultNotifier, urgentNotifier, clock);
        String text = service.welcome("ann", false);

        assertEquals("Welcome ann! It is 09:15:02", text);
        assertEquals(1, defaultNotifier.calls.size());
        assertEquals(new Call("ann", "Welcome ann! It is 09:15:02"), defaultNotifier.calls.get(0));
        assertTrue(urgentNotifier.calls.isEmpty());
    }

    @Test
    void welcomeSendsToUrgentNotifierWhenUrgent() {
        var defaultNotifier = new FakeNotifier();
        var urgentNotifier = new FakeNotifier();
        var clock = Clock.fixed(Instant.parse("2026-09-25T09:15:02Z"), ZoneId.of("UTC"));

        var service = new WelcomeService(defaultNotifier, urgentNotifier, clock);
        String text = service.welcome("bob", true);

        assertEquals("Welcome bob! It is 09:15:02", text);
        assertEquals(1, urgentNotifier.calls.size());
        assertEquals(new Call("bob", "Welcome bob! It is 09:15:02"), urgentNotifier.calls.get(0));
        assertTrue(defaultNotifier.calls.isEmpty());
    }
}
