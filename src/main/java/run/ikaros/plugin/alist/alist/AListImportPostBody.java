package run.ikaros.plugin.alist.alist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AListImportPostBody {
    @Schema(requiredMode = REQUIRED, description = "导入的路径，需要进行base64进行编码。")
    private String path;
}
