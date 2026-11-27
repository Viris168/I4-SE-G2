package edu.itc.shop.web.dto;

import edu.itc.shop.domain.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record RoleChangeRequest(@NotEmpty Set<Role> roles) {
}
