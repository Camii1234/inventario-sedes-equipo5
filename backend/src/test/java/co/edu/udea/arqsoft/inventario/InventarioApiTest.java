package co.edu.udea.arqsoft.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Pruebas de reglas de negocio y del contrato HTTP de la API (H2 en modo MySQL). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventarioApiTest {

    private static final String V = "X-API-Version";
    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CacheManager caches;

    /** Cada prueba revierte su transacción, así que la caché no debe conservar páginas de otra prueba. */
    @BeforeEach
    void limpiarCache() {
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    }

    private static MockHttpServletRequestBuilder v1(MockHttpServletRequestBuilder builder) {
        return builder.header(V, "1");
    }

    private static String cuerpo(Object sedeId, String sku, String nombre, Object stock) {
        return """
                {"sedeId":%s,"sku":%s,"nombre":%s,"stockInicial":%s}""".formatted(
                sedeId, sku == null ? "null" : "\"" + sku + "\"", nombre == null ? "null" : "\"" + nombre + "\"", stock);
    }

    private MvcResult crear(Object sedeId, String sku, String nombre, Object stock) throws Exception {
        return mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(sedeId, sku, nombre, stock))).andReturn();
    }

    private JsonNode leer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Nested
    @DisplayName("1. GET /api/inventario?sedeId")
    class ConsultaInventario {

        @Test
        @DisplayName("200: identifica la sede y devuelve solo sus productos ordenados por SKU")
        void inventarioDeSede() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "1")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                    .andExpect(jsonPath("$.sede.id").value(1))
                    .andExpect(jsonPath("$.sede.nombre").value("Sede Medellín Centro"))
                    .andExpect(jsonPath("$.totalProductos").value(3))
                    .andExpect(jsonPath("$.productos[*].sku", contains("ACE-001", "AZU-001", "LEC-001")))
                    .andExpect(jsonPath("$.productos[*].sedeId", everyItem(is(1))))
                    .andExpect(jsonPath("$._links.self.href", endsWith("/api/inventario?sedeId=1&page=0&size=10")))
                    .andExpect(jsonPath("$.productos[0]._links.self.href", containsString("/api/productos/")));
        }

        @Test
        @DisplayName("El SKU compartido tiene stock distinto y los productos de una sede no aparecen en otra")
        void skuCompartidoConStockDistinto() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "1")))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ACE-001')].stock", contains(35)))
                    .andExpect(jsonPath("$.productos[*].sku", not(hasItem("CAF-001"))));
            mvc.perform(v1(get("/api/inventario").param("sedeId", "2")))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ACE-001')].stock", contains(12)))
                    .andExpect(jsonPath("$.productos[*].sku", not(hasItem("LEC-001"))))
                    .andExpect(jsonPath("$.productos[*].sedeId", everyItem(is(2))));
        }

        @Test
        @DisplayName("200 con colección vacía para una sede existente sin productos")
        void sedeVacia() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "3")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sede.id").value(3))
                    .andExpect(jsonPath("$.productos", empty()))
                    .andExpect(jsonPath("$.totalProductos").value(0));
        }

        @Test
        @DisplayName("404 para una sede inexistente")
        void sedeInexistente() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "999")))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                    .andExpect(jsonPath("$.codigo").value("SEDE_NO_ENCONTRADA"))
                    .andExpect(jsonPath("$.detail").value("No existe una sede con id 999"));
        }

        @Test
        @DisplayName("400 si falta sedeId")
        void faltaSedeId() throws Exception {
            mvc.perform(v1(get("/api/inventario")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                    .andExpect(jsonPath("$.codigo").value("PARAMETRO_REQUERIDO"));
        }

        @Test
        @DisplayName("400 si sedeId no es numérico, es cero o negativo")
        void sedeIdInvalido() throws Exception {
            for (String valor : new String[] {"abc", "0", "-4", "1.5"}) {
                mvc.perform(v1(get("/api/inventario").param("sedeId", valor)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
            }
        }
    }

    @Nested
    @DisplayName("2. POST /api/productos")
    class RegistroProductos {

        @Test
        @DisplayName("201 con Location, recurso persistido, SKU normalizado y enlaces navegables")
        void creaProducto() throws Exception {
            MvcResult r = crear(1, "  arr-001 ", "  Arroz   blanco de 1 kg ", 20);
            assertThat(r.getResponse().getStatus()).isEqualTo(201);
            JsonNode body = leer(r);
            assertThat(body.get("sku").asText()).isEqualTo("ARR-001");
            assertThat(body.get("nombre").asText()).isEqualTo("Arroz blanco de 1 kg");
            assertThat(body.get("stock").asInt()).isEqualTo(20);
            assertThat(body.get("sedeId").asLong()).isEqualTo(1);
            String self = body.at("/_links/self/href").asText();
            String inventario = body.at("/_links/inventario/href").asText();
            assertThat(r.getResponse().getHeader("Location")).isEqualTo(self);
            assertThat(self).endsWith("/api/productos/" + body.get("id").asLong());
            assertThat(inventario).endsWith("/api/inventario?sedeId=1");

            // Seguir los enlaces devueltos, enviando otra vez X-API-Version.
            mvc.perform(v1(get(self)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(body.get("id").asLong()))
                    .andExpect(jsonPath("$.stock").value(20));
            mvc.perform(v1(get(inventario)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sede.id").value(1))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ARR-001')].stock", contains(20)));
        }

        @Test
        @DisplayName("409 si el SKU se repite en la misma sede (sin importar espacios/mayúsculas) y se conserva el stock")
        void duplicadoMismaSede() throws Exception {
            assertThat(crear(1, "ARR-001", "Arroz blanco de 1 kg", 20).getResponse().getStatus()).isEqualTo(201);
            MvcResult dup = crear(1, " arr-001", "Arroz repetido", 99);
            assertThat(dup.getResponse().getStatus()).isEqualTo(409);
            assertThat(dup.getResponse().getContentType()).startsWith("application/problem+json");
            JsonNode problema = leer(dup);
            assertThat(problema.get("codigo").asText()).isEqualTo("SKU_DUPLICADO_EN_SEDE");
            assertThat(problema.at("/productoExistente/stock").asInt()).isEqualTo(20);

            mvc.perform(v1(get("/api/inventario").param("sedeId", "1")))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ARR-001')].stock", contains(20)))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ARR-001')]", hasSize(1)));
        }

        @Test
        @DisplayName("El mismo SKU se registra en otra sede con cantidad diferente")
        void mismoSkuOtraSede() throws Exception {
            assertThat(crear(1, "ARR-001", "Arroz blanco de 1 kg", 20).getResponse().getStatus()).isEqualTo(201);
            assertThat(crear(2, "ARR-001", "Arroz blanco de 1 kg", 8).getResponse().getStatus()).isEqualTo(201);
            mvc.perform(v1(get("/api/inventario").param("sedeId", "1")))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ARR-001')].stock", contains(20)));
            mvc.perform(v1(get("/api/inventario").param("sedeId", "2")))
                    .andExpect(jsonPath("$.productos[?(@.sku=='ARR-001')].stock", contains(8)));
        }

        @Test
        @DisplayName("Stock inicial cero es válido")
        void stockCero() throws Exception {
            MvcResult r = crear(3, "SAL-001", "Sal refinada 500 g", 0);
            assertThat(r.getResponse().getStatus()).isEqualTo(201);
            assertThat(leer(r).get("stock").asInt()).isZero();
        }

        @Test
        @DisplayName("400 con detalle por campo si el stock es negativo")
        void stockNegativo() throws Exception {
            mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(1, "NEG-001", "Producto negativo", -5)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                    .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.errores[0].campo").value("stockInicial"));
        }

        @Test
        @DisplayName("400 si el stock no es entero (decimal o texto)")
        void stockNoEntero() throws Exception {
            for (String stock : new String[] {"2.5", "\"muchos\""}) {
                mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo(1, "DEC-001", "Producto decimal", stock)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"))
                        .andExpect(jsonPath("$.detail", containsString("stockInicial")));
            }
        }

        @Test
        @DisplayName("400 si faltan campos obligatorios o tienen formato inválido")
        void camposObligatorios() throws Exception {
            mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(null, "  ", null, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("sedeId")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("sku")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("stockInicial")));
            mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(1, "ARR 001", "X".repeat(121), 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("sku")))
                    .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")));
        }

        @Test
        @DisplayName("404 si la sede del producto no existe")
        void sedeInexistente() throws Exception {
            mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(999, "ARR-001", "Arroz", 5)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("SEDE_NO_ENCONTRADA"));
        }

        @Test
        @DisplayName("400 si el cuerpo no es JSON")
        void cuerpoIlegible() throws Exception {
            mvc.perform(v1(post("/api/productos")).contentType(MediaType.APPLICATION_JSON).content("{sedeId:"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        }
    }

    @Nested
    @DisplayName("7. HATEOAS y detalle de producto")
    class Hipermedia {

        @Test
        @DisplayName("404 para un producto inexistente")
        void productoInexistente() throws Exception {
            mvc.perform(v1(get("/api/productos/999999")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
        }

        @Test
        @DisplayName("GET /api/almacenes lista las sedes con enlace a su inventario")
        void almacenes() throws Exception {
            mvc.perform(v1(get("/api/almacenes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.almacenes", hasSize(3)))
                    .andExpect(jsonPath("$.almacenes[2]._links.inventario.href", endsWith("/api/inventario?sedeId=3")))
                    .andExpect(jsonPath("$._links.self.href", endsWith("/api/almacenes")));
        }

        @Test
        @DisplayName("Los enlaces usan el host público recibido del proxy (X-Forwarded-Host)")
        void enlacesDetrasDelProxy() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "1"))
                            .header("X-Forwarded-Host", "localhost:3000")
                            .header("X-Forwarded-Proto", "http"))
                    .andExpect(jsonPath("$._links.self.href").value("http://localhost:3000/api/inventario?sedeId=1&page=0&size=10"));
        }
    }

    @Nested
    @DisplayName("6. Versionamiento por X-API-Version")
    class Versionamiento {

        @Test
        @DisplayName("400 VERSION_REQUERIDA si falta el encabezado, en todos los endpoints de negocio")
        void encabezadoAusente() throws Exception {
            for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[] {
                    get("/api/inventario").param("sedeId", "1"), get("/api/almacenes"), get("/api/productos/1"),
                    post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, "V-001", "X", 1))}) {
                mvc.perform(req)
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                        .andExpect(jsonPath("$.codigo").value("VERSION_REQUERIDA"));
            }
        }

        @Test
        @DisplayName("400 VERSION_NO_SOPORTADA con las versiones admitidas si la versión no existe")
        void versionNoSoportada() throws Exception {
            mvc.perform(get("/api/inventario").param("sedeId", "1").header(V, "2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VERSION_NO_SOPORTADA"))
                    .andExpect(jsonPath("$.versionesSoportadas", contains("1")))
                    .andExpect(jsonPath("$.detail", containsString("Versiones admitidas")))
                    .andExpect(header().string("X-API-Supported-Versions", "1"));
        }

        @Test
        @DisplayName("No se crea nada si la versión es inválida")
        void postSinVersionNoPersiste() throws Exception {
            mvc.perform(post("/api/productos").header(V, "9").contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo(3, "VER-001", "Prueba versión", 1))).andExpect(status().isBadRequest());
            mvc.perform(v1(get("/api/inventario").param("sedeId", "3"))).andExpect(jsonPath("$.productos", empty()));
        }

        @Test
        @DisplayName("El contrato OpenAPI abre sin encabezado y declara X-API-Version")
        void openApiSinEncabezado() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/inventario'].get.parameters[*].name", hasItem("X-API-Version")))
                    .andExpect(jsonPath("$.paths['/api/productos'].post.parameters[*].name", hasItem("X-API-Version")));
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("14. Paginación y caché")
    class PaginacionYCache {

        @Test
        @DisplayName("page y size dividen el inventario y los enlaces first/prev/next/last navegan entre páginas")
        void paginacion() throws Exception {
            MvcResult r = mvc.perform(v1(get("/api/inventario").param("sedeId", "1").param("page", "0").param("size", "2")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productos[*].sku", contains("ACE-001", "AZU-001")))
                    .andExpect(jsonPath("$.totalProductos").value(3))
                    .andExpect(jsonPath("$.totalUnidades").value(125))
                    .andExpect(jsonPath("$.pagina.numero").value(0))
                    .andExpect(jsonPath("$.pagina.tamano").value(2))
                    .andExpect(jsonPath("$.pagina.totalPaginas").value(2))
                    .andExpect(jsonPath("$._links.prev").doesNotExist())
                    .andExpect(jsonPath("$._links.last.href", endsWith("page=1&size=2")))
                    .andReturn();
            String next = leer(r).at("/_links/next/href").asText();
            mvc.perform(v1(get(next)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productos[*].sku", contains("LEC-001")))
                    .andExpect(jsonPath("$._links.next").doesNotExist())
                    .andExpect(jsonPath("$._links.prev.href", endsWith("page=0&size=2")));
        }

        @Test
        @DisplayName("400 si page es negativo o size está fuera de 1..100")
        void parametrosInvalidos() throws Exception {
            for (String[] p : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"size", "x"}}) {
                mvc.perform(v1(get("/api/inventario").param("sedeId", "1").param(p[0], p[1])))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
            }
        }

        @Test
        @DisplayName("La segunda consulta sale de la caché (X-Cache: HIT) y un registro la invalida")
        void cache() throws Exception {
            mvc.perform(v1(get("/api/inventario").param("sedeId", "2"))).andExpect(header().string("X-Cache", "MISS"));
            mvc.perform(v1(get("/api/inventario").param("sedeId", "2"))).andExpect(header().string("X-Cache", "HIT"));
            assertThat(crear(2, "CCH-001", "Producto caché", 4).getResponse().getStatus()).isEqualTo(201);
            mvc.perform(v1(get("/api/inventario").param("sedeId", "2")))
                    .andExpect(header().string("X-Cache", "MISS"))
                    .andExpect(jsonPath("$.productos[?(@.sku=='CCH-001')].stock", contains(4)));
        }
    }
}
