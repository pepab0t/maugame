package dev.cerios.maugame.websocket.security;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

@UtilityClass
public class UserHelper {
    public static AppUserDetails getUserDetails() {
        Authentication auth = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return ((AppUserDetails) auth.getPrincipal());
    }
}
