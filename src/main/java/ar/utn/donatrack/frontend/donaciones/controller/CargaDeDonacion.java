package ar.utn.donatrack.frontend.donaciones.controller;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que el donante escribe en /donante/donar: un título y uno o más bienes.
 * Spring completa la lista con bienes[0], bienes[1], etc.
 */
public class CargaDeDonacion {

    private String descripcion;
    private List<Bien> bienes = new ArrayList<>();

    public static CargaDeDonacion vacia() {
        CargaDeDonacion carga = new CargaDeDonacion();
        carga.bienes.add(new Bien());
        return carga;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public List<Bien> getBienes() {
        return bienes;
    }

    public void setBienes(List<Bien> bienes) {
        this.bienes = bienes == null ? new ArrayList<>() : bienes;
    }

    public static class Bien {
        private String subcategoria;
        private String subcategoriaOtra;
        private String detalle;
        private String cantidad = "1";
        private String unidad = "unidades";
        private String esNuevo = "true";
        private String fechaVencimiento;
        private String foto;

        public String getSubcategoria() { return subcategoria; }
        public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }
        public String getSubcategoriaOtra() { return subcategoriaOtra; }
        public void setSubcategoriaOtra(String subcategoriaOtra) { this.subcategoriaOtra = subcategoriaOtra; }
        public String getDetalle() { return detalle; }
        public void setDetalle(String detalle) { this.detalle = detalle; }
        public String getCantidad() { return cantidad; }
        public void setCantidad(String cantidad) { this.cantidad = cantidad; }
        public String getUnidad() { return unidad; }
        public void setUnidad(String unidad) { this.unidad = unidad; }
        public String getEsNuevo() { return esNuevo; }
        public void setEsNuevo(String esNuevo) { this.esNuevo = esNuevo; }
        public String getFechaVencimiento() { return fechaVencimiento; }
        public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
        public String getFoto() { return foto; }
        public void setFoto(String foto) { this.foto = foto; }
    }
}
