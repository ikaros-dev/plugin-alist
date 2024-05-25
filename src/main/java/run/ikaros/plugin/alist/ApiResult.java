package run.ikaros.plugin.alist;

import lombok.Data;

import java.util.Map;

@Data
public class ApiResult {
    private int code;
    private String message;
    private Map<String, Object> data;
}
