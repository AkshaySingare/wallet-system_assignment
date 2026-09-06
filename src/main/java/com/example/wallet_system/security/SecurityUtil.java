package com.example.wallet_system.security;

import com.example.wallet_system.exception.ApiException;
import com.example.wallet_system.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated user's id from the security context. */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "No authenticated user in context");
        }
        return principal.getId();
    }
}
