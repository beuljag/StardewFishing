package kr.server.stardewfishing;

import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class StardewFishingPlugin extends JavaPlugin {

    private FishingData data;
    private FishingListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new FishingData(this);
        data.load();

        listener = new FishingListener(this, data);
        getServer().getPluginManager().registerEvents(listener, this);

        FishingCommand command = new FishingCommand(this, data);
        PluginCommand registered = getCommand("fishing");
        registered.setExecutor(command);
        registered.setTabCompleter(command);

        getLogger().info("낚시대 " + data.rods().size() + "개, 물고기 " + data.fish().size()
                + "개, 낚시터 " + data.zones().size() + "개를 불러왔습니다.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.abortAll();
        }
    }

    void reload() {
        reloadConfig();
        data.load();
    }

    Component message(String key) {
        return ItemSpec.color(getConfig().getString("messages." + key, key));
    }
}
