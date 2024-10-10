package run.ikaros.plugin.alist.alist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AListToken {
    private String username;
    /**
     * // todo save encrypted password.
     */
    private String password;
    private String token;
    /**
     * expire by ms.
     */
    private Long expire;
    private String url;
    private boolean enableAutoTokenRefresh;
}
