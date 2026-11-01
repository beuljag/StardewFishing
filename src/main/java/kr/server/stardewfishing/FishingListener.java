package kr.server.stardewfishing;

import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

final class FishingListener implements Listener {

    private static final Title.Times BITE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200));

    private final StardewFishingPlugin plugin;
    private final FishingData data;
    private final Random random = new Random();
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    FishingListener(StardewFishingPlugin plugin, FishingData data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        // 미니게임 중에는 찌를 회수하지 못하게 한다.
        if (sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> applyBiteTime(player, hook);
            case BITE -> announceBite(player, hook);
            case CAUGHT_FISH -> startGame(event, player, hook);
            default -> {
            }
        }
    }

    private void applyBiteTime(Player player, FishHook hook) {
        FishingData.Rod rod = data.rodOf(rodInHand(player));
        if (rod != null) {
            hook.setWaitTime(rod.biteMin(), rod.biteMax());
        }
    }

    private void announceBite(Player player, FishHook hook) {
        if (isFishable(data.zoneAt(hook.getLocation())) && rodFor(player) != null) {
            player.showTitle(Title.title(plugin.message("bite-title"), plugin.message("bite-subtitle"), BITE_TIMES));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        }
    }

    private void startGame(PlayerFishEvent event, Player player, FishHook hook) {
        FishingData.Zone zone = data.zoneAt(hook.getLocation());
        FishingData.Rod rod = rodFor(player);
        if (!isFishable(zone) || rod == null) {
            if (zone == null && !plugin.getConfig().getBoolean("allow-vanilla-outside-zones", true)) {
                event.setCancelled(true);
                hook.remove();
                player.sendMessage(plugin.message("outside-zone"));
            }
            return;
        }

        event.setCancelled(true);
        event.setExpToDrop(0);
        GameSession session = new GameSession(plugin, this, player, hook, rod, data.pickFish(zone, random));
        sessions.put(player.getUniqueId(), session);
        session.begin();
    }

    private boolean isFishable(FishingData.Zone zone) {
        return zone != null && !zone.fish().isEmpty();
    }

    /** 들고 있는 낚시대의 능력치. 일반 낚시대는 vanilla-rod 설정을 따르고, 설정이 없으면 null. */
    private FishingData.Rod rodFor(Player player) {
        FishingData.Rod rod = data.rodOf(rodInHand(player));
        if (rod != null) {
            return rod;
        }
        return data.rods().get(plugin.getConfig().getString("vanilla-rod", ""));
    }

    /** 바닐라와 같이 주 손을 먼저 확인한다. */
    ItemStack rodInHand(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.FISHING_ROD) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off.getType() == Material.FISHING_ROD ? off : null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.abort();
        }
    }

    void sessionEnded(Player player) {
        sessions.remove(player.getUniqueId());
    }

    void abortAll() {
        new ArrayList<>(sessions.values()).forEach(GameSession::abort);
    }
}
