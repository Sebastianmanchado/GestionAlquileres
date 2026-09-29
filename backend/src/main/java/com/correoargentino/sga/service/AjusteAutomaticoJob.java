package com.correoargentino.sga.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AjusteAutomaticoJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AjusteAutomaticoJob.class);

    private final AjusteAutomaticoService service;
    private final boolean enabled;

    public AjusteAutomaticoJob(
            AjusteAutomaticoService service,
            @Value("${sga.ajustes.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        revisarSiElIndiceEstaVencido();
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

    @Scheduled(
            cron = "0 0 * * * *",
            zone = "${sga.ajustes.zone:America/Argentina/Buenos_Aires}")
    public void revisarCadaHora() {
        revisarSiElIndiceEstaVencido();
    }

    private void revisarSiElIndiceEstaVencido() {
        if (!enabled) return;
        try {
            service.asegurarIndiceDelDia();
        } catch (RuntimeException e) {
            log.error("No se pudo actualizar el IPC del día: {}", e.getMessage());
        }
    }
}
