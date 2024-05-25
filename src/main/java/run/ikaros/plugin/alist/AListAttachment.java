package run.ikaros.plugin.alist;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AListAttachment {
    private List<String> paths;
    private String name;
    private Long size;
    private Boolean is_dir;
    private LocalDateTime modified;
    private LocalDateTime created;
    private String sign;
    private String thumb;
    private int type;
    private String hashinfo;
    private String hash_info;
    private String raw_url;
    private String readme;
    private String header;
    private String provider;
}
