package run.ikaros.plugin.alist;


import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.ikaros.api.plugin.BasePlugin;

@Slf4j
@Component
public class AListPlugin extends BasePlugin {

    public static final String NAME = "PluginAList";

    public AListPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("plugin ["+ NAME +"] start success");
    }

    @Override
    public void stop() {
        log.info("plugin ["+ NAME +"] stop success");
    }

    @Override
    public void delete() {
        log.info("plugin ["+ NAME +"] delete success");
    }
}