package ar.utn.donatrack.frontend.comun.util;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MapasTest {

    @Test
    void leeSinRomperConValoresAusentesOMalTipados() {
        assertTrue(Mapas.mapa(null).isEmpty());
        assertTrue(Mapas.lista("no es lista").isEmpty());
        assertEquals("", Mapas.texto(Map.of(), "x"));
        assertEquals("5", Mapas.texto(Map.of("x", 5), "x"));
    }

    @Test
    void devuelveLaListaCuandoCorresponde() {
        assertEquals(1, Mapas.lista(List.of(Map.of("a", 1))).size());
    }

    @Test
    void comparaEmailsSinMayusculasNiEspacios() {
        assertTrue(Mapas.igual(" Cynthia@Mail.com ", "cynthia@mail.com"));
        assertFalse(Mapas.igual("", ""));
        assertFalse(Mapas.igual(null, "a"));
    }
}
