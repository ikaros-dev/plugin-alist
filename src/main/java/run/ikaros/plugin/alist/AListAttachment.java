package run.ikaros.plugin.alist;

import lombok.Data;

import java.util.List;

@Data
public class AListAttachment {
    private List<String> paths;
    private String name;
    private Long size;
    private Boolean is_dir;
    private String modified;
    private String created;
    private String sign;
    private String thumb;
    private int type;
    private String hashinfo;
    private Object hash_info;
    private String raw_url;
    private String readme;
    private String header;
    private String provider;
    private Long parentId;
    private Long id;
}
