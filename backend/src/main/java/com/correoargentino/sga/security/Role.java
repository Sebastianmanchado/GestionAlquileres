package com.correoargentino.sga.security;

/**
 * Roles del MVP. Mientras no exista Keycloak, el rol se resuelve localmente
 * (config o cabecera X-Role) y determina los permisos de la UI y la API.
 */
public enum Role {
    ANALISTA("Analista de alquileres"),
    SUPERVISOR("Supervisor regional"),
    AUDITOR("Auditor");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canEdit() {
        return this == ANALISTA;
    }

    public boolean canApprove() {
        return this == SUPERVISOR;
    }

    public boolean isReadOnly() {
        return this == AUDITOR;
    }

    public static Role fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANALISTA;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ANALISTA;
        }
    }
}
