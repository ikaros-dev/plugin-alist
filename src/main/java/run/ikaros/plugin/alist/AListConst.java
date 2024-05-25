package run.ikaros.plugin.alist;

public interface AListConst {
    interface ConfigMapKey {
        String apiBaseUrl = "apiBaseUrl";
        String apiUsername = "apiUsername";
        String apiPassword = "apiPassword";
        String enableAutoTokenRefresh = "enableAutoTokenRefresh";
        String apiToken = "apiToken";
        String apiExpire = "apiExpire";
    }
    interface Attachment {
        String DEFAULT_PARENT_NAME = "AList";
    }
}
