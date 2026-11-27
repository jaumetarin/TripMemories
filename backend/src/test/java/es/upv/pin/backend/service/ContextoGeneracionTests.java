package es.upv.pin.backend.service;
import es.upv.pin.backend.client.*;
import es.upv.pin.backend.dto.*;
import es.upv.pin.backend.model.UsuarioAutenticado;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ContextoGeneracionTests {
 @Test void generacionConservaCuentaYBloqueaGeoapifyConCuotaAgotada() {
  var limite=mock(LimiteProveedorService.class);
  var usuario=mock(UsuarioAutenticado.class); var id=UUID.randomUUID(); when(usuario.getId()).thenReturn(id);
  when(limite.reservar(id,"proveedores",120)).thenReturn(60L);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario,null,List.of()));
  try {
   var builder=RestClient.builder().baseUrl("https://prueba.test").requestInterceptor(new ControlLlamadasProveedor(limite));
   var servidor=MockRestServiceServer.bindTo(builder).build();
   var geo=new GeoapifyClient(builder.build(),"prueba");
   var config=mock(ConfiguracionItinerarioService.class);
   var entrada=mock(ConfiguracionItinerarioDto.class);
   when(config.validar(entrada)).thenReturn(entrada);
   when(entrada.preferencias()).thenReturn(new PreferenciasDto(List.of(es.upv.pin.backend.model.CategoriaTuristica.ARTE_MUSEOS),List.of(),List.of(),es.upv.pin.backend.model.NivelGasto.MODERADO,3.0));
   when(entrada.disponibilidad()).thenReturn(new FechaHoraDto(java.time.LocalDateTime.parse("2026-12-01T10:00"),java.time.LocalDateTime.parse("2026-12-01T18:00"),1));
   var preparacion=mock(PreparacionCandidatosService.class);
   when(preparacion.preparar(entrada)).thenAnswer(inv->{assertSame(usuario,SecurityContextHolder.getContext().getAuthentication().getPrincipal()); geo.autocompletar("Madrid");return List.of();});
   var servicio=new EvaluacionCandidatosService(config,preparacion,mock(AiProvider.class),mock(ResearchProvider.class),120);
   assertThrows(es.upv.pin.backend.exception.LimiteProveedorException.class,()->servicio.evaluar(entrada));
   verify(limite).reservar(id,"proveedores",120);servidor.verify();
  } finally {SecurityContextHolder.clearContext();}
 }
}
