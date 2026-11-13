package ar.utn.donatrack.frontend.donaciones.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Formulario de donación y ubicación de la entidad (Entrega 6)")
class FormularioYUbicacionTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bien(Map<String, Object> cuerpo) {
        return ((List<Map<String, Object>>) cuerpo.get("bienes")).getFirst();
    }

    @Nested
    @DisplayName("FormularioDonacion")
    class Formulario {

        @Test
        @DisplayName("Un bien con vencimiento va sin esNuevo: el servicio rechaza los dos datos juntos")
        void perecederoSinEsNuevo() {
            Map<String, Object> cuerpo = FormularioDonacion.cuerpo(UUID.randomUUID(), "Leche", "Alimentos", null,
                    null, 6, "litros", true, "2026-12-31", null);

            assertThat(bien(cuerpo)).containsEntry("fechaVencimiento", "2026-12-31").doesNotContainKey("esNuevo");
        }

        @Test
        @DisplayName("Sin vencimiento manda esNuevo")
        void conEstado() {
            Map<String, Object> cuerpo = FormularioDonacion.cuerpo(UUID.randomUUID(), "Camperas", "Abrigo", null,
                    null, 3, "unidades", false, "", null);

            assertThat(bien(cuerpo)).containsEntry("esNuevo", false).doesNotContainKey("fechaVencimiento");
        }

        @Test
        @DisplayName("La foto es una URL opcional: si viene, va recortada; si no, no se manda")
        void foto() {
            Map<String, Object> conFoto = FormularioDonacion.cuerpo(UUID.randomUUID(), "Camperas", "Abrigo", null,
                    null, 3, "unidades", true, null, "  https://fotos.org/campera.jpg ");
            Map<String, Object> sinFoto = FormularioDonacion.cuerpo(UUID.randomUUID(), "Camperas", "Abrigo", null,
                    null, 3, "unidades", true, null, " ");

            assertThat(bien(conFoto)).containsEntry("foto", "https://fotos.org/campera.jpg");
            assertThat(bien(sinFoto)).doesNotContainKey("foto");
        }

        @Test
        @DisplayName("Con \"Otro\", la subcategoría es lo que escribió la persona")
        void otro() {
            Map<String, Object> cuerpo = FormularioDonacion.cuerpo(UUID.randomUUID(), "Cañas", "Otro", " Pesca ",
                    null, 1, "unidades", true, null, null);

            assertThat(bien(cuerpo)).containsEntry("subcategoria", "Pesca");
        }

        @Test
        @DisplayName("Tres camperas usadas y dos kilos de alimento salen en un solo pedido, y el alimento lleva vencimiento sin esNuevo")
        void variosBienes() {
            CargaDeDonacion carga = new CargaDeDonacion();
            carga.setDescripcion("Camperas y alimentos");
            CargaDeDonacion.Bien camperas = new CargaDeDonacion.Bien();
            camperas.setSubcategoria("Abrigo");
            camperas.setDetalle("Camperas de invierno");
            camperas.setCantidad("3");
            camperas.setUnidad("unidades");
            camperas.setEsNuevo("false");
            CargaDeDonacion.Bien alimentos = new CargaDeDonacion.Bien();
            alimentos.setSubcategoria("Alimentos");
            alimentos.setCantidad("2");
            alimentos.setUnidad("kg");
            alimentos.setEsNuevo("true");
            alimentos.setFechaVencimiento("2026-12-31");
            carga.setBienes(List.of(camperas, alimentos));

            Map<String, Object> cuerpo = FormularioDonacion.cuerpo(UUID.randomUUID(), carga);

            assertThat(cuerpo).containsEntry("descripcion", "Camperas y alimentos");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bienes = (List<Map<String, Object>>) cuerpo.get("bienes");
            assertThat(bienes).hasSize(2);
            assertThat(bienes.get(0)).containsEntry("subcategoria", "Abrigo")
                    .containsEntry("cantidad", 3)
                    .containsEntry("esNuevo", false)
                    .doesNotContainKey("fechaVencimiento");
            assertThat(bienes.get(1)).containsEntry("subcategoria", "Alimentos")
                    .containsEntry("cantidad", 2)
                    .containsEntry("unidad", "kg")
                    .containsEntry("fechaVencimiento", "2026-12-31")
                    .doesNotContainKey("esNuevo");
        }

        @Test
        @DisplayName("Sin tipo no se arma el pedido")
        void exigeElTipo() {
            CargaDeDonacion carga = new CargaDeDonacion();
            carga.setDescripcion("Sin tipo");
            carga.setBienes(List.of(new CargaDeDonacion.Bien()));

            assertThat(FormularioDonacion.validar(carga)).contains("elegí el tipo");
        }
    }

    @Nested
    @DisplayName("Ubicacion")
    class Coordenadas {

        @Test
        @DisplayName("Sin coordenadas no agrega nada a la dirección")
        void sinCoordenadas() {
            assertThat(Ubicacion.desde(null, " ")).isEmpty();
        }

        @Test
        @DisplayName("Acepta la coma decimal y devuelve números")
        void conComa() {
            assertThat(Ubicacion.desde("-34,6037", "-58.3816"))
                    .containsEntry("latitud", -34.6037).containsEntry("longitud", -58.3816);
        }

        @Test
        @DisplayName("Rechaza una sola coordenada, un texto que no es número o un valor fuera de rango")
        void invalidas() {
            assertThatThrownBy(() -> Ubicacion.desde("-34.6", "")).hasMessageContaining("latitud y la longitud");
            assertThatThrownBy(() -> Ubicacion.desde("abc", "-58")).hasMessageContaining("no es un número");
            assertThatThrownBy(() -> Ubicacion.desde("-95", "-58")).hasMessageContaining("entre -90 y 90");
            assertThatThrownBy(() -> Ubicacion.desde("-34", "190")).hasMessageContaining("entre -180 y 180");
        }
    }

    @Test
    @DisplayName("Los links de \"Mis donaciones\" conservan estado, varias subcategorías (codificadas) y la página")
    void linksDeMisDonaciones() {
        assertThat(DonanteDonacionesController.urlDeMisDonaciones("ENTREGADA", List.of("Útiles escolares", "Abrigo"), 2))
                .isEqualTo("/donante/donaciones?estado=ENTREGADA&subcategoria=%C3%9Atiles%20escolares&subcategoria=Abrigo&page=2");
        assertThat(DonanteDonacionesController.urlDeMisDonaciones("", List.of(), 1)).isEqualTo("/donante/donaciones");
    }
}
