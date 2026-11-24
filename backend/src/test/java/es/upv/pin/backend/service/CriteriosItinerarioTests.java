package es.upv.pin.backend.service;

import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static es.upv.pin.backend.model.CategoriaTuristica.*;

class CriteriosItinerarioTests {
    private LugarDto lugar(String id, boolean documentado, CategoriaTuristica... categorias) {
        return new LugarDto(id, id, null, 40.4, -3.7, List.of(categorias), List.of(), null, TipoLugar.OTRO, documentado, documentado ? 7 : 0);
    }
    private ConfiguracionItinerarioDto config(List<CategoriaTuristica> intereses, SeleccionLugarDto... selecciones) {
        return new ConfiguracionItinerarioDto(null, null, "", new PreferenciasDto(intereses, List.of(), List.of(), NivelGasto.MODERADO, 3.0), null, List.of(selecciones));
    }
    @Test void laMuestraDeIaNoPierdeLaVariedadNiLosReferentesAntesDeCientoVeinte() {
        List<LugarDto> lugares = new ArrayList<>();
        for (int i = 0; i < 200; i++) lugares.add(lugar("m" + i, false, ARTE_MUSEOS));
        for (int i = 0; i < 200; i++) lugares.add(lugar("p" + i, false, NATURALEZA_PARQUES));
        var referente = lugar("z-referente", true, NATURALEZA_PARQUES);
        lugares.add(referente);
        var muestra = CriteriosItinerario.muestra(lugares, config(List.of(ARTE_MUSEOS, NATURALEZA_PARQUES)), 120);
        assertEquals(120, muestra.size());
        assertEquals(60, muestra.stream().filter(l -> l.categorias().contains(ARTE_MUSEOS)).count());
        assertEquals(60, muestra.stream().filter(l -> l.categorias().contains(NATURALEZA_PARQUES)).count());
        assertTrue(muestra.contains(referente));
    }
    @Test void losElegidosNoCompitenConLasCuotasAutomaticasYLosEvitadosNoVuelven() {
        var obligatorio = lugar("obligatorio", false, ARTE_MUSEOS);
        var preferido = lugar("preferido", false, ARTE_MUSEOS);
        var evitado = lugar("evitado", true, NATURALEZA_PARQUES);
        var config = config(List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), new SeleccionLugarDto(obligatorio, EstadoLugar.OBLIGATORIO),
                new SeleccionLugarDto(preferido, EstadoLugar.PREFERIDO), new SeleccionLugarDto(evitado, EstadoLugar.EVITADO));
        var muestra = CriteriosItinerario.muestra(List.of(evitado, lugar("m", false, ARTE_MUSEOS), lugar("p", false, NATURALEZA_PARQUES)), config, 4);
        assertEquals(List.of("obligatorio", "preferido", "m", "p"), muestra.stream().map(LugarDto::id).toList());
    }
    @Test void unLugarCompartidoSeReservaParaLaCategoriaConMenosAlternativas() {
        var compartido = lugar("compartido", true, ARTE_MUSEOS, NATURALEZA_PARQUES);
        var muestra = CriteriosItinerario.muestra(List.of(compartido, lugar("museo", false, ARTE_MUSEOS)),
                config(List.of(ARTE_MUSEOS, NATURALEZA_PARQUES)), 2);
        assertEquals(2, muestra.size());
        assertEquals(2, muestra.stream().map(LugarDto::id).distinct().count());
        assertEquals(NATURALEZA_PARQUES, CriteriosItinerario.categoria(compartido, List.of(ARTE_MUSEOS, NATURALEZA_PARQUES), Map.of(), muestra));
    }
}
