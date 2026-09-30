package com.correoargentino.sga.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AjusteAutomaticoJob {

    private static final Logger log = LoggerFactory.getLogger(AjusteAutomaticoJob.class);

    private final AjusteAutomaticoService service;
    private final boolean enabled;

    public AjusteAutomaticoJob(
            AjusteAutomaticoService service,
            @Value("${sga.ajustes.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${sga.ajustes.cron:0 30 6 * * *}",
            zone = "${sga.ajustes.zone:America/Argentina/Buenos_Aires}")
    public void run() {
        if (!enabled) return;
        try {
            service.ejecutar();
        } catch (RuntimeException e) {
            log.error("El job de ajustes automáticos falló: {}", e.getMessage());
        }
    }
}
