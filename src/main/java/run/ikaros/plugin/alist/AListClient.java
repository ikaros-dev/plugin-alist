package run.ikaros.plugin.alist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.utils.StringUtils;
import run.ikaros.api.plugin.event.PluginConfigMapUpdateEvent;
import run.ikaros.plugin.alist.AListConst.ConfigMapKey;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@Retryable
public class AListClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private HttpHeaders httpHeaders = new HttpHeaders();
    private String baseUrl = "";
    private AListToken token;
    private Disposable refreshTokenTaskDisposable;

    private final ReactiveCustomClient customClient;

    public AListClient(ReactiveCustomClient customClient) {
        this.customClient = customClient;
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
        if (StringUtils.isBlank(token.getUrl())
        || StringUtils.isBlank(token.getUsername())
            || StringUtils.isBlank(token.getPassword())) {
            log.warn("token url or username or password is null or empty for token: {}",
                    JsonUtils.obj2Json(token));
            return Mono.empty();
        }

        if (StringUtils.isBlank(token.getToken())) {
            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("username", token.getUsername());
            bodyMap.put("password", token.getPassword());
            String body = null;
            try {
                body = new ObjectMapper().writeValueAsString(bodyMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> httpEntity = new HttpEntity<>(body, httpHeaders);

            ResponseEntity<ApiResult> responseEntity =
                    restTemplate.postForEntity(token.getUrl() + API.AUTH_TOKEN, httpEntity, ApiResult.class);
            ApiResult apiResult = responseEntity.getBody();
            log.debug("post token to alist for token: {}", JsonUtils.obj2Json(token));
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
//                .onErrorResume(NotFoundException.class, e -> customClient.create(AListToken
//                                .builder()
//                                .build()));
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

    @EventListener({ApplicationReadyEvent.class, PluginConfigMapUpdateEvent.class})
    public Mono<Void> afterPropertiesSet(ApplicationEvent event) throws Exception {
        if (event instanceof ApplicationReadyEvent readyEvent) {
            log.debug("afterPropertiesSet ApplicationReadyEvent");
            if (Objects.isNull(token)) {
                // token is null, get config from db.
                return updateOperateByToken().then();
            }

            return Mono.empty();

        }

        if (event instanceof PluginConfigMapUpdateEvent updateEvent) {
//            log.debug("afterPropertiesSet PluginConfigMapUpdateEvent for event: {}", new ObjectMapper().writeValueAsString(updateEvent));
            if(Objects.isNull(updateEvent.getConfigMap()) || !AListPlugin.NAME.equals(updateEvent.getConfigMap().getName())) {
                return Mono.empty();
            }
            return updateOperateByToken().then();
        }

        return Mono.empty();

    }

}