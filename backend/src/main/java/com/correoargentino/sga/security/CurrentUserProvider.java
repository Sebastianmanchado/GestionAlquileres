package com.correoargentino.sga.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resuelve el rol y el usuario "actual" del MVP. Puerto que en el futuro
 * implementará Keycloak a partir del claim "sub" y los roles del JWT.
 */
@Component
public class CurrentUserProvider {

    private final Role defaultRole;

    public CurrentUserProvider(@Value("${sga.local-role:ANALISTA}") String localRole) {
        this.defaultRole = Role.fromString(localRole);
    }

    public Role currentRole() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String header = request.getHeader("X-Role");
            if (header != null && !header.isBlank()) {
                return Role.fromString(header);
            }
        }
        return defaultRole;
    }

    public String currentUsername() {
        return switch (currentRole()) {
            case ANALISTA -> "jmartinez";
            case SUPERVISOR -> "csuarez";
            case AUDITOR -> "lauditor";
        };
    }

    public String currentDisplayName() {
        return switch (currentRole()) {
            case ANALISTA -> "Juan Martínez";
            case SUPERVISOR -> "Carla Suárez";
            case AUDITOR -> "Laura Auditora";
        };
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
