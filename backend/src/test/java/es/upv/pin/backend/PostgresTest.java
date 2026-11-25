package es.upv.pin.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Una base efímera para las pruebas: nunca conecta con el PostgreSQL de desarrollo. */
public abstract class PostgresTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("rute_tests");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
