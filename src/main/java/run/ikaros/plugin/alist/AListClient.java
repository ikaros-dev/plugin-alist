package run.ikaros.plugin.alist;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.attachment.Attachment;
import run.ikaros.api.core.attachment.AttachmentConst;
import run.ikaros.api.core.attachment.AttachmentOperate;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.utils.StringUtils;
import run.ikaros.api.plugin.event.PluginConfigMapUpdateEvent;
import run.ikaros.api.store.enums.AttachmentType;
import run.ikaros.plugin.alist.AListConst.ConfigMapKey;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class AListClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private HttpHeaders httpHeaders = new HttpHeaders();
    private AListToken token;
    private Disposable refreshTokenTaskDisposable;

    private final ReactiveCustomClient customClient;
    private final AttachmentOperate attachmentOperate;

    public AListClient(ReactiveCustomClient customClient, AttachmentOperate attachmentOperate) {
        this.customClient = customClient;
        this.attachmentOperate = attachmentOperate;
    }

    public Mono<Void> doImportFilesFromAListPath(List<String> paths) {
        if (Objects.isNull(paths) || paths.isEmpty()) {
            return Mono.error(new IllegalArgumentException("paths is null or empty"));
        }
        return Mono.justOrEmpty(token)
                .switchIfEmpty(updateOperateByToken())
                .flatMap(token -> attachmentOperate.findByTypeAndParentIdAndName(AttachmentType.Directory, AttachmentConst.ROOT_DIRECTORY_ID,
                        AListConst.Attachment.DEFAULT_PARENT_NAME))
                // 获取导入的父目录ID，不存在则创建
                .switchIfEmpty(attachmentOperate.createDirectory(AttachmentConst.ROOT_DIRECTORY_ID, AListConst.Attachment.DEFAULT_PARENT_NAME))
                .map(Attachment::getId)
                .flatMap(rootParentId -> mkdirByLastPath(paths, rootParentId))
                .flatMap(parentId -> createAttachmentRecursively(paths, parentId));
    }

    private Mono<Void> createAttachmentRecursively(List<String> paths, Long parentId) {
        if (Objects.isNull(paths) || paths.isEmpty()) {
            return Mono.error(new IllegalArgumentException("paths is null or empty"));
        }

        if (StringUtils.isBlank(token.getUrl())
                || StringUtils.isBlank(token.getUsername())
                || StringUtils.isBlank(token.getPassword())) {
            log.warn("token url or username or password is null or empty for token: {}",
                    JsonUtils.obj2Json(token));
            return Mono.empty();
        }

        AListAttachment[] attachments = fetchAttachments(paths);
        return Flux.fromStream(Arrays.stream(attachments))
                .map(aListAttachment -> {
                    List<String> newPaths = new ArrayList<>(paths);
                    newPaths.add(aListAttachment.getName());
                    aListAttachment.setPaths(newPaths);
                    aListAttachment.setParentId(parentId);
                    return aListAttachment;
                })
                .flatMap(aListAttachment -> {
                    String path = getPathByPathArr(aListAttachment.getPaths());
                    return Mono.just(fetchAttachmentDetail(path, aListAttachment));
                })
                .flatMap(this::saveAListAttachment)
                .flatMap(aListAttachment -> {
                    if (aListAttachment.getIs_dir()) {
                        List<String> paths1 = aListAttachment.getPaths();
                        return createAttachmentRecursively(paths1, aListAttachment.getId());
                    } else {
                        return Mono.empty();
                    }

                })
                .then();
    }

    @Retryable
    public AListAttachment[] fetchAttachments(List<String> paths) {
        Map<String, String> bodyMap = new HashMap<>();
        String path = getPathByPathArr(paths);
        bodyMap.put("path", path);
        String body = JsonUtils.obj2Json(bodyMap);
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);

        ResponseEntity<ApiResult> responseEntity =
                restTemplate.postForEntity(token.getUrl() + API.FS_LIST, httpEntity, ApiResult.class);
        ApiResult apiResult = responseEntity.getBody();
        if (apiResult != null && apiResult.getCode() == 200) {
            Object content = apiResult.getData().get("content");
            return JsonUtils.obj2Arr(content, new TypeReference<AListAttachment[]>() {
            });
        } else {
            log.error("post get alist attachments for path: {} and apiResult: {}", path, apiResult);
            if (Objects.nonNull(apiResult) && apiResult.getCode() == 401) {
                updateOperateByToken().subscribe();
            }
        }

        return new AListAttachment[0];
    }

    private Mono<Long> mkdirByLastPath(List<String> paths, Long parentId) {
        String dirName = paths.get(paths.size() - 1);
        if (dirName.startsWith("/")) dirName = dirName.substring(1);
        if (dirName.endsWith("/")) dirName = dirName.substring(0, dirName.length() - 1);
        return attachmentOperate.findByTypeAndParentIdAndName(AttachmentType.Directory, parentId, dirName)
                .switchIfEmpty(attachmentOperate.createDirectory(parentId, dirName))
                .flatMap(attachment -> attachmentOperate.findByTypeAndParentIdAndName(
                        attachment.getType(), attachment.getParentId(), attachment.getName()
                ))
                .map(Attachment::getId);
    }

    private Mono<AListAttachment> saveAListAttachment(AListAttachment aListAttachment) {
        return attachmentOperate.save(
                        Attachment.builder()
                                .parentId(aListAttachment.getParentId())
                                .name(aListAttachment.getName())
                                .updateTime(LocalDateTime.now())
                                .type(aListAttachment.getIs_dir() ? AttachmentType.Directory : AttachmentType.File)
                                .size(aListAttachment.getSize())
                                .url(aListAttachment.getRaw_url())
                                .fsPath(getPathByPathArr(aListAttachment.getPaths()))
                                .build())
                .flatMap(attachment -> attachmentOperate.findByTypeAndParentIdAndName(
                        attachment.getType(), attachment.getParentId(), attachment.getName()
                ))
                .map(attachment -> {
                    aListAttachment.setParentId(attachment.getParentId());
                    aListAttachment.setId(attachment.getId());
                    return aListAttachment;
                });
    }


    /**
     * @see <https://alist.nn.ci/zh/guide/>
     */
    public interface API {
        String AUTH_TOKEN = "/api/auth/login";
        String FS_LIST = "/api/fs/list";
        String FS_GET = "/api/fs/get";
    }

    public Mono<AListToken> refreshToken() {
        return refreshToken(false);
    }

    @Retryable
    public Mono<AListToken> refreshToken(boolean refresh) {
        if (StringUtils.isBlank(token.getUrl())
        || StringUtils.isBlank(token.getUsername())
            || StringUtils.isBlank(token.getPassword())) {
            log.warn("token url or username or password is null or empty for token: {}",
                    JsonUtils.obj2Json(token));
            return Mono.empty();
        }

        if (StringUtils.isBlank(token.getToken()) || refresh) {
            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("username", token.getUsername());
            bodyMap.put("password", token.getPassword());
            String body = JsonUtils.obj2Json(bodyMap);
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);

            ResponseEntity<ApiResult> responseEntity =
                    restTemplate.postForEntity(token.getUrl() + API.AUTH_TOKEN, httpEntity, ApiResult.class);
            ApiResult apiResult = responseEntity.getBody();
            // log.debug("post token to alist for token: {}", JsonUtils.obj2Json(token));
            if (apiResult != null && apiResult.getCode() == 200) {
                Object token1 = apiResult.getData().get("token");
                httpHeaders.set("Authorization", String.valueOf(token1));
                token.setToken(String.valueOf(token1));
                return customClient.update(token2cm(token))
                        .then(Mono.just(token));
            } else {
                log.error("post token to alist for apiResult: {}", apiResult);
            }
        }
        if (StringUtils.isNotBlank(token.getToken())) {
            httpHeaders.set("Authorization", token.getToken());
        }
        if (token.isEnableAutoTokenRefresh()) {
            startRefreshTokenTask();
        } else {
            if (Objects.nonNull(refreshTokenTaskDisposable)) {
                refreshTokenTaskDisposable.dispose();
                refreshTokenTaskDisposable = null;
            }
        }
        return Mono.just(token);
    }

    private String getPathByPathArr(List<String> paths) {
        StringBuilder path = new StringBuilder("/");
        for (String p: paths) {
            path.append(p).append("/");
        }
        String pStr = path.toString();
        if (pStr.endsWith("/")) {
            pStr = pStr.substring(0, pStr.length() - 1);
        }
        return pStr;
    }

    @Retryable
    private AListAttachment fetchAttachmentDetail(String path, AListAttachment attachment) {
        if (StringUtils.isEmpty(path)) {
            return attachment;
        }

        if (StringUtils.isBlank(token.getUrl())
                || StringUtils.isBlank(token.getUsername())
                || StringUtils.isBlank(token.getPassword())) {
            log.warn("token url or username or password is null or empty for token: {}",
                    JsonUtils.obj2Json(token));
            return attachment;
        }

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("path", path);
        String body = JsonUtils.obj2Json(bodyMap);
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);

        ResponseEntity<ApiResult> responseEntity =
                restTemplate.postForEntity(token.getUrl() + API.FS_GET, httpEntity, ApiResult.class);
        ApiResult apiResult = responseEntity.getBody();
        if (apiResult != null && apiResult.getCode() == 200) {
            AListAttachment aListAttachment = JsonUtils.json2obj(JsonUtils.obj2Json(apiResult.getData()), AListAttachment.class);
            if (Objects.isNull(aListAttachment)) return attachment;
            aListAttachment.setPaths(attachment.getPaths());
            aListAttachment.setId(attachment.getId());
            aListAttachment.setParentId(attachment.getParentId());
            return aListAttachment;
        } else {
            log.error("post alist attachment details for apiResult: {}", apiResult);
        }
        return attachment;
    }


    public Mono<ConfigMap> getConfigMap() {
        return customClient.findOne(ConfigMap.class, AListPlugin.NAME);
    }

    public AListToken cm2token(ConfigMap cm) {
        if (token == null) {
            token = new AListToken();
        }
        Map<String, String> cmd = cm.getData();
        if (cmd == null) {
            cmd = new HashMap<>();
        }
        token.setUrl(cmd.get(ConfigMapKey.apiBaseUrl));
        token.setUsername(cmd.get(ConfigMapKey.apiUsername));
        token.setPassword(cmd.get(ConfigMapKey.apiPassword));
        token.setEnableAutoTokenRefresh(
                Boolean.parseBoolean(
                StringUtils.isNotBlank(cmd.get(ConfigMapKey.enableAutoTokenRefresh))
                        ? cmd.get(ConfigMapKey.enableAutoTokenRefresh) : "false"
        ));
        token.setToken(cmd.get(ConfigMapKey.apiToken));
        token.setExpire(
                StringUtils.isNotBlank(cmd.get(ConfigMapKey.apiExpire))
                ? Long.parseLong(cmd.get(ConfigMapKey.apiExpire)) : 0
        );
        // log.debug("token: {}", token);
        return token;
    }

    public ConfigMap token2cm(AListToken aListToken) {
        token = aListToken;
        ConfigMap cm = new ConfigMap();
        Map<String, String> cmd = new HashMap<>();
        cm.setName(AListPlugin.NAME);
        cm.setData(cmd);
        cmd.put(ConfigMapKey.apiBaseUrl, token.getUrl());
        cmd.put(ConfigMapKey.apiUsername, token.getUsername());
        cmd.put(ConfigMapKey.apiPassword, token.getPassword());
        cmd.put(ConfigMapKey.enableAutoTokenRefresh, String.valueOf(token.isEnableAutoTokenRefresh()));
        cmd.put(ConfigMapKey.apiToken, token.getToken());
        cmd.put(ConfigMapKey.apiExpire, String.valueOf(token.getExpire()));
        return cm;
    }

    public Mono<AListToken> getToken() {
        return getConfigMap().map(this::cm2token);
    }

    public void startRefreshTokenTask() {
        refreshTokenTaskDisposable = Flux.interval(Duration.ofHours(47))
                .flatMap(tick -> refreshToken())
                .subscribeOn(Schedulers.newSingle("PluginAListRefreshTokenTask", true))
                .subscribe();
    }

    public Mono<AListToken> updateOperateByToken() {
        return getToken().flatMap(aListToken -> refreshToken());
    }

    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> initAListToken() {
        log.debug("init alist token.");
        if (Objects.isNull(token)) {
            // token is null, get config from db.
            return updateOperateByToken().then();
        }
        return Mono.empty();
    }


    @EventListener(PluginConfigMapUpdateEvent.class)
    public Mono<Void> onConfigMapUpdated(PluginConfigMapUpdateEvent updateEvent) throws Exception {
        // log.debug("afterPropertiesSet PluginConfigMapUpdateEvent for event: {}", new ObjectMapper().writeValueAsString(updateEvent));
        if(Objects.isNull(updateEvent.getConfigMap()) || !AListPlugin.NAME.equals(updateEvent.getConfigMap().getName())) {
            return Mono.empty();
        }
        return updateOperateByToken().then();
    }

}
