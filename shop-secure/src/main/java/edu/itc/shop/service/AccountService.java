package edu.itc.shop.service;

import edu.itc.shop.domain.AuditType;
import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

@Service
public class AccountService {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AccountService(UserAccountRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public UserAccount register(String username, String rawPassword, Set<Role> roles) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        UserAccount account = new UserAccount(username, encoder.encode(rawPassword), roles);
        return users.save(account);
    }

    /** Replaces a user's roles. Takes effect with the user's next login or token refresh. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserAccount changeRoles(String username, Set<Role> roles) {
        UserAccount account = users.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Set<Role> before = new TreeSet<>(account.getRoles());
        account.setRoles(roles);
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        audit.record(AuditType.ROLE_CHANGED, username, before + " -> " + new TreeSet<>(roles) + " by " + actor);
        return account;
    }
}
