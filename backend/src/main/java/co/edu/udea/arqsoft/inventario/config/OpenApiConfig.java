package co.edu.udea.arqsoft.inventario.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI inventarioOpenApi() {
        return new OpenAPI().info(new Info()
                .title("API de Inventario por Sede - Equipo 5")
                .version(ApiVersion.V1)
                .description("""
                        Parcial práctico de Arquitectura de Software (Universidad de Antioquia).

                        **Versionamiento:** todas las rutas `/api/**` exigen el encabezado `X-API-Version: 1`.
                        Sin el encabezado la API responde 400 `VERSION_REQUERIDA`; con una versión distinta responde
                        400 `VERSION_NO_SOPORTADA` e indica las versiones admitidas.

                        **Formato:** los recursos se devuelven como `application/hal+json` con enlaces `_links`;
                        los errores como `application/problem+json` (RFC 9457) con un `codigo` estable."""));
    }

    /** Agrega X-API-Version (obligatorio, valor 1) a cada operación de negocio para que Swagger UI lo envíe. */
    @Bean
    OperationCustomizer encabezadoVersion() {
        return (operation, handlerMethod) -> {
            List<Parameter> parametros = operation.getParameters() == null
                    ? new ArrayList<>() : new ArrayList<>(operation.getParameters());
            parametros.removeIf(p -> ApiVersion.HEADER.equalsIgnoreCase(p.getName()));
            StringSchema esquema = new StringSchema();
            esquema.setDefault(ApiVersion.V1);
            esquema.setExample(ApiVersion.V1);
            parametros.add(0, new HeaderParameter()
                    .name(ApiVersion.HEADER)
                    .required(true)
                    .description("Versión de la API. Valor soportado: 1. Ausente o distinto de 1 responde 400.")
                    .schema(esquema)
                    .example(ApiVersion.V1));
            operation.setParameters(parametros);
            return operation;
        };
    }
}
