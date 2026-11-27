package edu.itc.shop.security;

import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public JpaUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        var builder = User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRoles().stream().map(Enum::name).toArray(String[]::new));

        if (!account.isEnabled()) {
            builder.disabled(true);
        }

        return builder.build();
    }
}
