package run.ikaros.plugin.alist;

import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.fn.builders.requestbody.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.ikaros.api.custom.GroupVersionKind;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.endpoint.CustomEndpoint;
import run.ikaros.api.infra.utils.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.encoding.Builder.encodingBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

@Slf4j
@Component
public class AListEndpoint implements CustomEndpoint {

    private GroupVersionKind groupVersionKind =
        new GroupVersionKind("plugin.ikaros.run", "v1alpha1", AListPlugin.NAME);

    private final AListClient aListClient;

    public AListEndpoint(AListClient aListClient) {
        this.aListClient = aListClient;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = groupVersionKind.group()
            + "/" + groupVersionKind.version()
            + "/" + groupVersionKind.kind();
        return SpringdocRouteBuilder.route()
                .POST("/alist/import", this::doImportFilesFromAList,
                        builder -> builder.operationId("ImportAlistFiles")
                                .tag(tag)
                                .description("Import Alist Files")
                                .requestBody(Builder.requestBodyBuilder()
                                        .required(true)
                                        .content(contentBuilder()
                                                .mediaType(MediaType.APPLICATION_JSON_VALUE))
                                        .implementation(AListImportPostBody.class)))
            .build();
    }

    Mono<ServerResponse> doImportFilesFromAList(ServerRequest request) {
        return request.bodyToMono(AListImportPostBody.class)
                .filter(Objects::nonNull)
                .map(AListImportPostBody::getPath)
                .map(path -> new String(Base64.getDecoder().decode(path), StandardCharsets.UTF_8))
                .map(path -> URLDecoder.decode(path, StandardCharsets.UTF_8))
                .doOnSuccess(s -> log.debug("paths: {}", s))
                .filter(StringUtils::isNotBlank)
                .map(path -> Arrays.stream(path.split("/")).filter(StringUtils::isNotBlank).toList())
                .doOnSuccess(strings -> log.debug("strings size: {}", strings.size()))
                .filter(Objects::nonNull)
                .filter(strings -> !strings.isEmpty())
                .flatMap(aListClient::doImportFilesFromAListPath)
                .then(ServerResponse.ok().build())
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    @Override
    public GroupVersionKind groupVersionKind() {
        return groupVersionKind;
    }
}
