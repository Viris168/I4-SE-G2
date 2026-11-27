package edu.itc.shop.web;

import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.service.AccountService;
import edu.itc.shop.service.AuditService;
import edu.itc.shop.web.dto.AuditEventResponse;
import edu.itc.shop.web.dto.RoleChangeRequest;
import edu.itc.shop.web.dto.UserProfileResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only: by URL rule in SecurityConfig and again by @PreAuthorize on the services. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditService audit;
    private final AccountService accounts;

    public AdminController(AuditService audit, AccountService accounts) {
        this.audit = audit;
        this.accounts = accounts;
    }

    @GetMapping("/audit")
    public List<AuditEventResponse> audit(@RequestParam(required = false) String username) {
        return audit.find(username).stream().map(AuditEventResponse::of).toList();
    }

    @PutMapping("/users/{username}/roles")
    public UserProfileResponse changeRoles(@PathVariable String username, @RequestBody @Valid RoleChangeRequest req) {
        UserAccount account = accounts.changeRoles(username, req.roles());
        List<String> roles = account.getRoles().stream().map(r -> "ROLE_" + r.name()).sorted().toList();
        return new UserProfileResponse(account.getUsername(), roles);
    }
}
