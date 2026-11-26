package es.upv.pin.backend.service;

import org.springframework.core.io.Resource;

/** El proveedor guarda claves opacas, no rutas ni nombres enviados por el usuario. */
public interface AlmacenamientoFotos {
    void guardar(String clave, byte[] contenido);
    Resource leer(String clave);
    void borrar(String clave);
}
