package edu.itc.shop.security;

import edu.itc.shop.domain.AuditType;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.service.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lockout driven by the authentication events of the login endpoint, not by the controller.
 * Only username/password logins count; Bearer-token authentications are ignored.
 */
@Component
public class LockoutListener {

    private final UserAccountRepository users;
    private final AuditService audit;

    public LockoutListener(UserAccountRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent e) {
        if (!(e.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String name = e.getAuthentication().getName();
        if (e instanceof AuthenticationFailureLockedEvent) {
            audit.record(AuditType.LOCKED, name, "login attempt while locked");
        } else if (e instanceof AuthenticationFailureBadCredentialsEvent) {
            users.findByUsername(name).ifPresentOrElse(u -> {
                u.registerFailure();
                if (u.isLocked()) {
                    audit.record(AuditType.LOCKED, name, "locked until " + u.getLockedUntil()
                            + " after " + u.getFailedAttempts() + " failures");
                } else {
                    audit.record(AuditType.LOGIN_FAILED, name,
                            "attempt " + u.getFailedAttempts() + "/" + UserAccount.MAX_FAILED_ATTEMPTS);
                }
            }, () -> audit.record(AuditType.LOGIN_FAILED, name, "unknown user"));
        } else {
            audit.record(AuditType.LOGIN_FAILED, name, e.getException().getClass().getSimpleName());
        }
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent e) {
        if (!(e.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String name = e.getAuthentication().getName();
        users.findByUsername(name).ifPresent(UserAccount::resetFailures);
        audit.record(AuditType.LOGIN_OK, name, null);
    }
}
