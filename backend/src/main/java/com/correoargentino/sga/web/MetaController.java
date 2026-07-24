package com.correoargentino.sga.web;

import com.correoargentino.sga.security.CurrentUserProvider;
import com.correoargentino.sga.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final CurrentUserProvider currentUser;

    public MetaController(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> meta() {
        Role role = currentUser.currentRole();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role.name());
        m.put("roleLabel", role.label());
        m.put("displayName", currentUser.currentDisplayName());
        m.put("canEdit", role.canEdit());
        m.put("canApprove", role.canApprove());
        m.put("isReadOnly", role.isReadOnly());
        return m;
    }
}
