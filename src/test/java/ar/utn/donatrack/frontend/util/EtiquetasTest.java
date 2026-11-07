package ar.utn.donatrack.frontend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EtiquetasTest {

    @Test
    void traduceLosEstadosConocidos() {
        assertEquals("En depósito", Etiquetas.estado("EN_DEPOSITO"));
        assertEquals("En camino", Etiquetas.estado("EN_TRASLADO"));
    }

    @Test
    void unEstadoDesconocidoSeMuestraLegible() {
        assertEquals("nuevo estado", Etiquetas.estado("NUEVO_ESTADO"));
    }

    @Test
    void laVarianteNuncaDependeSoloDelColor() {
        assertEquals("ok", Etiquetas.variante("ENTREGADA"));
        assertEquals("error", Etiquetas.variante("ENTREGA_FALLIDA"));
        assertEquals("check", Etiquetas.icono("ENTREGADA"));
        assertEquals("alert", Etiquetas.icono("ENTREGA_FALLIDA"));
    }

    @Test
    void elRecorridoTieneCincoParadasYMarcaLaActual() {
        var pasos = Etiquetas.recorrido("LISTA_PARA_ENTREGAR");
        assertEquals(5, pasos.size());
        assertEquals("hecho", pasos.get(0).getClase());
        assertEquals("hecho", pasos.get(1).getClase());
        assertEquals("actual", pasos.get(2).getClase());
        assertEquals("", pasos.get(3).getClase());
    }

    @Test
    void entregadaCompletaTodoElCamino() {
        var pasos = Etiquetas.recorrido("ENTREGADA");
        assertEquals("hecho fin", pasos.get(4).getClase());
        assertEquals("completado", pasos.get(2).getLeyenda());
    }

    @Test
    void unaEntregaFallidaMarcaProblemaEnLaParadaEnCamino() {
        var pasos = Etiquetas.recorrido("ENTREGA_FALLIDA");
        assertEquals("actual fallo", pasos.get(3).getClase());
        assertEquals("con problemas", pasos.get(3).getLeyenda());
        assertEquals("alert", pasos.get(3).getIcono());
    }
}
