package dev.peacechan.usermanagement.enumeration;

import java.util.List;

import static dev.peacechan.usermanagement.constant.Authority.*;

public enum Role {
    ROLE_USER(USER_AUTHORITIES),
    ROLE_HR(HR_AUTHORITIES),
    ROLE_MANAGER(MANAGER_AUTHORITIES),
    ROLE_ADMIN(ADMIN_AUTHORITIES),
    ROLE_SUPER_ADMIN(SUPER_ADMIN_AUTHORITIES);

    private List<String>  authorities;

    Role(String... authorities) {
        this.authorities = List.of(authorities);
    }

    public List<String> getAuthorities() {
        return authorities;
    }
}
