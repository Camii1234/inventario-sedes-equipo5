package co.edu.udea.arqsoft.inventario.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Caché local con Redis (numeral 14). Las páginas de inventario se guardan en Redis con TTL configurable
 * (spring.cache.redis.time-to-live) y se invalidan al registrar un producto.
 * Si Redis no está disponible, los errores de caché se registran y la consulta se resuelve en MySQL.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String INVENTARIO = "inventario";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Caché no disponible al leer {}::{}; se consulta MySQL. {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("No se pudo guardar {}::{} en caché. {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("No se pudo invalidar {}::{}. {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("No se pudo limpiar la caché {}. {}", cache.getName(), e.getMessage());
            }
        };
    }
}
