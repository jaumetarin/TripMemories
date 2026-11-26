package es.upv.pin.backend.config;

import es.upv.pin.backend.model.UsuarioAutenticado;
import es.upv.pin.backend.repository.UsuarioRepository;
import es.upv.pin.backend.service.AuthService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        var argon2 = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        var bcrypt = new BCryptPasswordEncoder();
        var encoder = new DelegatingPasswordEncoder("argon2id", Map.of("argon2id", argon2, "bcrypt", bcrypt));
        // Solo para hashes BCrypt previos del seed; las altas nuevas siempre usan Argon2id.
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
    @Bean
    AuthenticationManager authenticationManager(UsuarioRepository usuarios, PasswordEncoder encoder) {
        var proveedor = new DaoAuthenticationProvider(correo -> {
            var usuario = usuarios.buscarPorCorreo(AuthService.normalizarCorreo(correo))
                    .filter(u -> u.isCorreoVerificado()).orElseThrow(() -> new UsernameNotFoundException("Credenciales no válidas."));
            return new UsuarioAutenticado(usuario);
        });
        proveedor.setPasswordEncoder(encoder);
        proveedor.setUserDetailsPasswordService((principal, hash) -> {
            usuarios.actualizarHash(principal.getUsername(), hash);
            return new UsuarioAutenticado(usuarios.buscarPorCorreo(principal.getUsername()).orElseThrow());
        });
        return new ProviderManager(proveedor);
    }
    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager manager,
            HttpSessionSecurityContextRepository contexto, HttpSessionCsrfTokenRepository csrf,
            SessionAuthenticationStrategy estrategia, @Qualifier("corsConfigurationSource") CorsConfigurationSource origenes) throws Exception {
        http.authenticationManager(manager)
                .securityContext(c -> c.securityContextRepository(contexto))
                .sessionManagement(s -> s.sessionAuthenticationStrategy(estrategia))
                .csrf(c -> c.csrfTokenRepository(csrf))
                .cors(c -> c.configurationSource(origenes))
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable()).httpBasic(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login", "/api/auth/reenviar", "/api/auth/verificar", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, error) -> responder(res, 401, "SESION_REQUERIDA", "Inicia sesión para continuar."))
                        .accessDeniedHandler((req, res, error) -> {
                            if (error instanceof CsrfException) responder(res, 403, "CSRF_INVALIDO", "Actualiza la sesión de seguridad y vuelve a intentarlo.");
                            else responder(res, 403, "ACCESO_DENEGADO", "No tienes permiso para realizar esta operación.");
                        }))
                .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> responder(res, 200, "SESION_CERRADA", "Has cerrado sesión.")));
        return http.build();
    }
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${auth.frontend-url}") String frontend) {
        var url = URI.create(frontend);
        String origen = url.getScheme() + "://" + url.getAuthority();
        var config = new CorsConfiguration();
        if ("localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost())) {
            String puerto = url.getPort() == -1 ? "" : ":" + url.getPort();
            config.setAllowedOrigins(List.of(url.getScheme() + "://localhost" + puerto, url.getScheme() + "://127.0.0.1" + puerto));
        } else config.setAllowedOrigins(List.of(origen));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
    @Bean("correosExecutor")
    Executor correosExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("correo-verificacion-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
    private static void responder(jakarta.servlet.http.HttpServletResponse respuesta, int estado, String codigo, String mensaje) throws java.io.IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json");
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.setHeader("Cache-Control", "no-store");
        respuesta.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\"}");
    }
}
