package edu.itc.shop.security;

import edu.itc.shop.domain.AuditType;
import edu.itc.shop.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Logs authentication successes/failures and authorization denials to the SECURITY logger, and
 * writes denials of authenticated users (the 403s) to the audit table.
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    private final AuditService audit;

    public SecurityEventLogger(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("login.ok user={} type={}", auth.getName(), auth.getClass().getSimpleName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("login.failed user={} reason={}",
                event.getAuthentication().getName(),
                event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> event) {
        Supplier<Authentication> supplier = event.getAuthentication();
        Authentication auth = supplier == null ? null : supplier.get();
        String user = auth == null ? "anonymous" : auth.getName();
        String object = describe(event.getObject());
        log.warn("access.denied user={} object={}", user, object);
        // anonymous denials become 401s and are not worth a row each
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            audit.record(AuditType.ACCESS_DENIED, user, object);
        }
    }

    private static String describe(Object obj) {
        if (obj instanceof MethodInvocation mi) {
            String args = Arrays.stream(mi.getArguments())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return mi.getMethod().getDeclaringClass().getSimpleName()
                    + "." + mi.getMethod().getName() + "(" + args + ")";
        }
        if (obj instanceof HttpServletRequest req) {
            return req.getMethod() + " " + req.getRequestURI();
        }
        return String.valueOf(obj);
    }
}
