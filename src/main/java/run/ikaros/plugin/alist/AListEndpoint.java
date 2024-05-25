package run.ikaros.plugin.alist;

import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.fn.builders.requestbody.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.ikaros.api.custom.GroupVersionKind;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.endpoint.CustomEndpoint;

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
                                        .implementation(AListImportPostBody.class)))
            .build();
    }

    Mono<ServerResponse> doImportFilesFromAList(ServerRequest request) {
        return request.bodyToMono(AListImportPostBody.class)
                .map(AListImportPostBody::getPath)
                .flatMap(path -> aListClient.doImportFilesFromAListPath(path))
                .then(ServerResponse.ok().build());
    }

    @Override
    public GroupVersionKind groupVersionKind() {
        return groupVersionKind;
    }
}
