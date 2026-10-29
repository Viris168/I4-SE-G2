package edu.itc.shop.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final RequestScopeContext requestScopeContext;

    public AuditAspect(RequestScopeContext requestScopeContext) {
        this.requestScopeContext = requestScopeContext;
    }

    @Around("@annotation(edu.itc.shop.aspect.Audited) || @within(edu.itc.shop.aspect.Audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String requestId;
        String user;
        try {
            requestId = requestScopeContext.getRequestId();
            user = requestScopeContext.getUser();
        } catch (Exception e) {
            requestId = "N/A (outside HTTP request)";
            user = "system";
        }

        String what = joinPoint.getSignature().toShortString();
        Instant when = Instant.now();

        log.info("[AUDIT] [RequestId: {}] WHO: {} | WHAT: {} | WHEN: {}", requestId, user, what, when);

        return joinPoint.proceed();
    }
}
