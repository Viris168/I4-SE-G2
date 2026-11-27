package edu.itc.shop.service;

import edu.itc.shop.domain.AuditEvent;
import edu.itc.shop.domain.AuditType;
import edu.itc.shop.repository.AuditEventRepository;
import edu.itc.shop.security.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the security_audit table. Every row is committed in its own transaction (REQUIRES_NEW),
 * so an audit entry survives even when the business transaction around it rolls back.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditType type, String username, String detail) {
        events.save(new AuditEvent(Instant.now(), type, clip(username, 100), clip(clientIp(), 64),
                clip(MDC.get(RequestIdFilter.MDC_KEY), 64), clip(detail, 500)));
    }

    /** Newest first, at most 200 rows; optionally only one user's events. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AuditEvent> find(String username) {
        return (username == null || username.isBlank())
                ? events.findTop200ByOrderByIdDesc()
                : events.findTop200ByUsernameOrderByIdDesc(username);
    }

    /** The current request's remote address; null outside a request (e.g. the start-up seeder). */
    private static String clientIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr()
                : null;
    }

    private static String clip(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
