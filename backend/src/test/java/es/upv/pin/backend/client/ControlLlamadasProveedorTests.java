package es.upv.pin.backend.client;

import es.upv.pin.backend.service.LimiteProveedorService;
import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.exception.LimiteProveedorException;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ControlLlamadasProveedorTests {
    final LimiteProveedorService limite = mock(LimiteProveedorService.class);
    final UUID cuenta = UUID.randomUUID();
    @BeforeEach void sesion() {
        var usuario = mock(UsuarioAutenticado.class);
        when(usuario.getId()).thenReturn(cuenta);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario,null,List.of()));
    }
    @AfterEach void salir() { SecurityContextHolder.clearContext(); }
    @Test void cacheGeoapifyFuncionaInclusoConCuotaAgotada() {
        var builder = RestClient.builder().baseUrl("https://prueba.test").requestInterceptor(new ControlLlamadasProveedor(limite));
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new GeoapifyClient(builder.build(),"prueba",new LimiteConsultasGeoapify(3,0));
        servidor.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"features\":[]}",MediaType.APPLICATION_JSON));
        cliente.buscarLugares("valencia","tourism.sights",10);
        when(limite.reservar(cuenta,"proveedores",120)).thenReturn(60L);
        cliente.buscarLugares("valencia","tourism.sights",10);
        var error=assertThrows(LimiteProveedorException.class,()->cliente.buscarLugares("madrid","tourism.sights",10));
        assertEquals(60L,error.getEsperaSegundos());
        verify(limite,times(2)).reservar(cuenta,"proveedores",120);
        servidor.verify();
    }
    @Test void datosInvalidosYClaveAusenteNoConsumen() {
        var builder = RestClient.builder().requestInterceptor(new ControlLlamadasProveedor(limite));
        var cliente = new OpenRouteServiceClient(builder.build(),"prueba");
        assertThrows(IllegalArgumentException.class,()->cliente.calcularMatriz(List.of()));
        var geo = new GeoapifyClient(builder.build(),"",new LimiteConsultasGeoapify(3,0));
        assertThrows(IllegalStateException.class,()->geo.buscarLugares("valencia","tourism.sights",10));
        verifyNoInteractions(limite);
    }
    @Test void orsAgotadoNoEnviaPeticion() {
        var builder = RestClient.builder().baseUrl("https://prueba.test").requestInterceptor(new ControlLlamadasProveedor(limite));
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var cliente = new OpenRouteServiceClient(builder.build(),"prueba");
        when(limite.reservar(cuenta,"proveedores",120)).thenReturn(30L);
        assertThrows(LimiteProveedorException.class,()->cliente.calcularDesde(
            new es.upv.pin.backend.dto.PuntoRutaDto("a",39.4,-0.3),
            List.of(new es.upv.pin.backend.dto.PuntoRutaDto("b",39.5,-0.4))));
        verify(limite).reservar(cuenta,"proveedores",120);
        servidor.verify();
    }
    @Test void erroresExternosTambienConsumenUnIntento() {
        var builder=RestClient.builder().baseUrl("https://geo.test").requestInterceptor(new ControlLlamadasProveedor(limite));
        var servidor=MockRestServiceServer.bindTo(builder).build();
        var cliente=new GeoapifyClient(builder.build(),"prueba",new LimiteConsultasGeoapify(3,0));
        servidor.expect(method(HttpMethod.GET)).andRespond(withServerError());
        assertThrows(IllegalStateException.class,()->cliente.autocompletar("Madrid"));
        verify(limite).reservar(cuenta,"proveedores",120); servidor.verify();
    }
    @Test void agotamientoBloqueaAntesDeEnviarHttp() {
        var builder=RestClient.builder().baseUrl("https://geo.test").requestInterceptor(new ControlLlamadasProveedor(limite));
        var servidor=MockRestServiceServer.bindTo(builder).build();
        var cliente=new GeoapifyClient(builder.build(),"prueba",new LimiteConsultasGeoapify(3,0));
        when(limite.reservar(cuenta,"proveedores",120)).thenReturn(30L);
        var error=assertThrows(LimiteProveedorException.class,()->cliente.autocompletar("Madrid"));
        assertEquals(30,error.getEsperaSegundos()); servidor.verify();
    }
}
