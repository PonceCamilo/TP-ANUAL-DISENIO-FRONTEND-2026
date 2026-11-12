package ar.utn.donatrack.frontend.donaciones.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Candidatos y transiciones permitidas")
class CandidatosYTransicionesTest {

    @Nested
    @DisplayName("Candidatos")
    class LecturaDeCandidatos {

        private final UUID comedor = UUID.randomUUID();
        private final UUID escuela = UUID.randomUUID();
        private final UUID hogar = UUID.randomUUID();

        /** La forma real de GET /donaciones/{id}/candidatos. */
        private Map<String, Object> respuesta() {
            return Map.of(
                    "idDonacion", UUID.randomUUID().toString(),
                    "rankings", List.of(
                            Map.of("algoritmo", "COMPATIBILIDAD_SEMANTICA",
                                    "entidades", List.of(Map.of("id", comedor.toString()), Map.of("id", escuela.toString()))),
                            Map.of("algoritmo", "PRIORIDAD_SUBATENDIDOS",
                                    "entidades", List.of(Map.of("id", escuela.toString())))),
                    "coincidencias", List.of(Map.of("id", hogar.toString())));
        }

        @Test
        @DisplayName("Junta las entidades de todos los rankings y las coincidencias, sin repetir")
        void juntaSinRepetir() {
            assertThat(Candidatos.idsDeEntidades(respuesta()))
                    .containsExactly(comedor.toString(), escuela.toString(), hogar.toString());
        }

        @Test
        @DisplayName("Una entidad es candidata si aparece en algún ranking o en las coincidencias")
        void incluye() {
            assertThat(Candidatos.incluye(respuesta(), hogar)).isTrue();
            assertThat(Candidatos.incluye(respuesta(), UUID.randomUUID())).isFalse();
            assertThat(Candidatos.incluye(respuesta(), null)).isFalse();
        }

        @Test
        @DisplayName("Una respuesta sin rankings no tiene candidatas")
        void sinRankings() {
            assertThat(Candidatos.idsDeEntidades(Map.of())).isEmpty();
        }

        @Test
        @DisplayName("Los algoritmos se muestran con un nombre para personas")
        void nombres() {
            assertThat(Candidatos.nombreDelAlgoritmo("PRIORIDAD_SUBATENDIDOS")).isEqualTo("Prioridad a las menos atendidas");
            assertThat(Candidatos.nombreDelAlgoritmo("OTRO_ALGORITMO")).isEqualTo("otro algoritmo");
        }
    }

    @Nested
    @DisplayName("Transiciones")
    class TransicionesPermitidas {

        @Test
        @DisplayName("Una donación en depósito solo se puede marcar vencida a mano: la asignación tiene su propia pantalla")
        void enDeposito() {
            assertThat(Transiciones.deDonacion("EN_DEPOSITO")).containsExactly("VENCIDA");
        }

        @Test
        @DisplayName("Una donación asignada no ofrece cambios manuales: se despacha a logística")
        void asignada() {
            assertThat(Transiciones.deDonacion("ASIGNACION_REALIZADA")).isEmpty();
        }

        @Test
        @DisplayName("Entregada y vencida son estados finales")
        void finales() {
            assertThat(Transiciones.deDonacion("ENTREGADA")).isEmpty();
            assertThat(Transiciones.deDonacion("VENCIDA")).isEmpty();
            assertThat(Transiciones.deDonacion(null)).isEmpty();
        }

        @Test
        @DisplayName("Un donante activo puede pasar a inactivo o bloqueado; los otros dos solo vuelven a activo")
        void donante() {
            assertThat(Transiciones.deDonante("ACTIVO")).containsExactly("INACTIVO", "BLOQUEADO");
            assertThat(Transiciones.deDonante("BLOQUEADO")).containsExactly("ACTIVO");
            assertThat(Transiciones.deDonante("INACTIVO")).containsExactly("ACTIVO");
        }

        @Test
        @DisplayName("La entrega fallida y el bloqueo piden justificación")
        void justificacion() {
            assertThat(Transiciones.exigeJustificacion("ENTREGA_FALLIDA")).isTrue();
            assertThat(Transiciones.exigeJustificacion("BLOQUEADO")).isTrue();
            assertThat(Transiciones.exigeJustificacion("VENCIDA")).isFalse();
        }
    }
}
