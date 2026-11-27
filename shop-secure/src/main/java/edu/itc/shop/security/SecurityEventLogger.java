package edu.itc.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Logs authentication successes/failures and authorization denials to the SECURITY logger. */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

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
        log.warn("access.denied user={} object={}", user, describe(event.getObject()));
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
