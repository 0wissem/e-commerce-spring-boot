package org.example.orderservice.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Reads the caller's raw JWT so it can be forwarded to another service.
 *
 * WHY THIS EXISTS AS AN EXPLICIT CAPTURE RATHER THAN A LOOKUP AT CALL TIME:
 *
 * SecurityContextHolder stores the authentication in a ThreadLocal. The cross-service lookups
 * in OrderService.create() run on a SEPARATE thread pool via CompletableFuture, and that pool's
 * threads have an EMPTY security context — the ThreadLocal does not travel with the task.
 *
 * So the token must be read on the REQUEST thread, before going async, and passed in as a
 * value. Calling SecurityContextHolder from inside the async task returns null and the
 * downstream call goes out anonymous — which is exactly the 403 that broke order creation.
 *
 * (Spring can propagate the context with DelegatingSecurityContextExecutor; capturing the one
 * value we actually need is simpler and makes the dependency visible.)
 */
public final class CallerToken {

    private CallerToken() {}

    /** The caller's bearer token, or empty when the request is anonymous. */
    public static Optional<String> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken().getTokenValue());
        }
        return Optional.empty();
    }
}
