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
import run.ikaros.api.endpoint.CustomEndpoint;
import run.ikaros.api.infra.utils.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;

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
                .doOnSuccess(s -> log.debug("original path: {}", s))
                .map(path -> new String(Base64.getDecoder().decode(path), StandardCharsets.UTF_8))
                .doOnSuccess(s -> log.debug("base64 decoded path: {}", s))
                .map(path -> path.replace("+", "%2B"))
                .map(path -> URLDecoder.decode(path, StandardCharsets.UTF_8))
                .doOnSuccess(s -> log.debug("url decoded path: {}", s))
                .filter(StringUtils::isNotBlank)
                .flatMap(this::removeHttpPrefixIfExists)
                .doOnSuccess(s -> log.debug("relative path: {}", s))
                .map(path -> Arrays.stream(path.split("/")).filter(StringUtils::isNotBlank).toList())
                .doOnSuccess(strings -> log.debug("strings size: {}", strings.size()))
                .filter(Objects::nonNull)
                .filter(strings -> !strings.isEmpty())
                .flatMap(aListClient::doImportFilesFromAListPath)
                .then(ServerResponse.ok().build())
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * 支持直接复制AList浏览器URL，而无需去区分是不是相对路径。
     *
     * @param path 浏览器URL
     * @return 相对路径
     */
    private Mono<String> removeHttpPrefixIfExists(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return aListClient.getToken()
                    .map(AListToken::getUrl)
                    .map(url -> path.replace(url, ""))
                    .map(p -> p.startsWith("/") ? p : "/" + p);
        }
        return Mono.just(path);
    }

    @Override
    public GroupVersionKind groupVersionKind() {
        return groupVersionKind;
    }
}
