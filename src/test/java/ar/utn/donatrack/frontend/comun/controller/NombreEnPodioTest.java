package ar.utn.donatrack.frontend.comun.controller;

import ar.utn.donatrack.frontend.comun.seguridad.SesionUsuario;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Entrega 6 (F-D4): el podio nombra a cada persona con el nombre que da Incentivos. */
@DisplayName("Nombre en el podio del panel de la persona donante")
class NombreEnPodioTest {

    private final UUID ana = UUID.randomUUID();

    private SesionUsuario sesionDeAna() {
        SesionUsuario usuario = new SesionUsuario("ana@example.com", "Ana Pérez", SesionUsuario.DONANTE, "jwt",
                Instant.now().plusSeconds(600));
        usuario.setDonanteId(ana);
        return usuario;
    }

    @Test
    @DisplayName("A otras personas las nombra con su nombre visible, o 'Donante' si todavía no llegó")
    void otrasPersonas() {
        assertThat(PanelController.nombreEnPodio(UUID.randomUUID(), "Beto G.", sesionDeAna())).isEqualTo("Beto G.");
        assertThat(PanelController.nombreEnPodio(UUID.randomUUID(), "", sesionDeAna())).isEqualTo("Donante");
    }

    @Test
    @DisplayName("A la persona logueada la marca como 'vos'")
    void laPropia() {
        assertThat(PanelController.nombreEnPodio(ana, "Ana P.", sesionDeAna())).isEqualTo("Ana (vos)");
    }
}
