package kr.server.stardewfishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Random;

/** 플레이어 한 명의 미니게임 진행: 입력 읽기, 화면 그리기, 결과 처리. */
final class GameSession extends BukkitRunnable {

    private static final String CELL = "█";
    /** 텍스트 디스플레이 한 줄 높이(블록). */
    private static final float LINE_HEIGHT = 0.25f;

    private final StardewFishingPlugin plugin;
    private final FishingListener listener;
    private final Player player;
    private final FishHook hook;
    private final FishingData.Rod rod;
    private final FishingData.Fish fish;
    private final FishingGame game;
    private final Random random = new Random();

    private final boolean worldDisplay;
    private final int rows;
    private final double distance;
    private final double sideOffset;
    private final TextColor trackColor;
    private final TextColor barColor;
    private final TextColor barMissColor;
    private final TextColor fishColor;
    private final TextColor fishMissColor;

    private TextDisplay display;
    private Component lastFrame;
    private int age;

    GameSession(StardewFishingPlugin plugin, FishingListener listener, Player player, FishHook hook,
                FishingData.Rod rod, FishingData.Fish fish) {
        this.plugin = plugin;
        this.listener = listener;
        this.player = player;
        this.hook = hook;
        this.rod = rod;
        this.fish = fish;
        this.game = new FishingGame(rod.barHeight(), fish.difficulty(), fish.motion(), random);
        this.worldDisplay = !"ACTIONBAR".equalsIgnoreCase(plugin.getConfig().getString("display.mode"));
        this.rows = Math.max(8, plugin.getConfig().getInt("display.rows", 20));
        this.distance = plugin.getConfig().getDouble("display.distance", 2.5);
        this.sideOffset = plugin.getConfig().getDouble("display.side-offset", 1.0);
        this.trackColor = configColor("track", "#1E1E1E");
        this.barColor = configColor("bar", "#00FF00");
        this.barMissColor = configColor("bar-miss", "#00B400");
        this.fishColor = configColor("fish", "#00FFFF");
        this.fishMissColor = configColor("fish-miss", "#FF6A00");
    }

    private TextColor configColor(String key, String fallback) {
        String value = plugin.getConfig().getString("display.colors." + key, fallback);
        TextColor color = TextColor.fromHexString(value);
        if (color == null) {
            plugin.getLogger().warning("display.colors." + key + " 값이 잘못되었습니다: " + value);
            return TextColor.fromHexString(fallback);
        }
        return color;
    }

    void begin() {
        if (worldDisplay) {
            spawnDisplay();
        }
        player.clearTitle();
        player.sendMessage(plugin.message("game-start"));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
        runTaskTimer(plugin, 1L, 1L);
    }

    private void spawnDisplay() {
        float scale = (float) plugin.getConfig().getDouble("display.scale", 0.4);
        float height = rows * LINE_HEIGHT * scale;
        boolean seeThrough = plugin.getConfig().getBoolean("display.see-through", false);
        display = player.getWorld().spawn(displayLocation(), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.LEFT);
            entity.setLineWidth(200);
            entity.setSeeThrough(seeThrough);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(220, 0, 0, 0));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTeleportDuration(1);
            entity.setTransformation(new Transformation(
                    new Vector3f(0f, -height / 2f, 0f), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        player.showEntity(plugin, display);
    }

    private Location displayLocation() {
        Location eye = player.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw());
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        return eye.clone()
                .add(eye.getDirection().multiply(distance))
                .add(right.multiply(sideOffset));
    }

    @Override
    public void run() {
        if (!player.isOnline() || !hook.isValid() || hook.getWorld() != player.getWorld()) {
            abort();
            player.sendMessage(plugin.message("game-cancel"));
            return;
        }

        game.tick(player.isSneaking());
        render();
        age++;

        if (game.isWon()) {
            finish(true);
        } else if (game.isLost()) {
            finish(false);
        }
    }

    private void render() {
        if (!worldDisplay) {
            player.sendActionBar(horizontalFrame());
            return;
        }
        display.teleport(displayLocation());
        Component frame = verticalFrame();
        if (!frame.equals(lastFrame)) {
            display.text(frame);
            lastFrame = frame;
        }
        // 액션바는 3초 뒤 사라지므로 조작 안내를 주기적으로 다시 보낸다.
        if (age % 20 == 0) {
            player.sendActionBar(plugin.message("game-hint"));
        }
    }

    /** 왼쪽 칸은 낚시 트랙(찌와 초록 막대), 오른쪽 칸은 포획 게이지. */
    private Component verticalFrame() {
        int fishRow = rowOf(game.fishCenter());
        int filled = Math.round(game.progress() * rows);
        TextComponent.Builder builder = Component.text();
        for (int row = 0; row < rows; row++) {
            if (row > 0) {
                builder.append(Component.newline());
            }
            builder.append(Component.text(CELL + CELL + CELL, trackColor(row, fishRow)));
            builder.append(Component.text(" "));
            builder.append(Component.text(CELL, row >= rows - filled ? progressColor() : trackColor));
        }
        return builder.build();
    }

    private Component horizontalFrame() {
        int columns = rows * 2;
        int fishColumn = (int) (game.fishCenter() / FishingGame.TRACK * columns);
        TextComponent.Builder builder = Component.text();
        // 쉬프트를 누르면 오른쪽으로 움직이도록 트랙을 뒤집어 그린다.
        for (int column = columns - 1; column >= 0; column--) {
            float top = column * FishingGame.TRACK / columns;
            float bottom = (column + 1) * FishingGame.TRACK / columns;
            builder.append(Component.text("|", cellColor(column == fishColumn, top, bottom)));
        }
        builder.append(Component.text("  " + Math.round(game.progress() * 100) + "%", progressColor()));
        return builder.build();
    }

    private TextColor trackColor(int row, int fishRow) {
        float top = row * FishingGame.TRACK / rows;
        float bottom = (row + 1) * FishingGame.TRACK / rows;
        return cellColor(row == fishRow, top, bottom);
    }

    private TextColor cellColor(boolean isFish, float top, float bottom) {
        if (isFish) {
            return game.isFishInBar() ? fishColor : fishMissColor;
        }
        boolean inBar = bottom > game.barTop() && top < game.barBottom();
        if (inBar) {
            return game.isFishInBar() ? barColor : barMissColor;
        }
        return trackColor;
    }

    private TextColor progressColor() {
        float progress = game.progress();
        if (progress > 0.66f) {
            return TextColor.color(0x00FF00);
        }
        return TextColor.color(progress > 0.33f ? 0xFFFF00 : 0xFF2020);
    }

    private int rowOf(float y) {
        return Math.min(rows - 1, (int) (y / FishingGame.TRACK * rows));
    }

    private void finish(boolean caught) {
        cleanUp();
        if (caught) {
            reward();
        } else {
            player.sendMessage(plugin.message("game-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.6f);
        }
        rollBreak();
    }

    private void reward() {
        ItemStack item = fish.item().build();
        if (item == null) {
            plugin.getLogger().warning("물고기 " + fish.id() + "의 아이템을 만들 수 없습니다. ItemsAdder ID를 확인하세요: "
                    + fish.item().itemsAdderId());
            player.sendMessage(plugin.message("item-error"));
            return;
        }
        Component itemName = item.displayName();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (fish.exp() > 0) {
            player.giveExp(fish.exp());
        }
        player.sendMessage(plugin.message("game-catch").replaceText(TextReplacementConfig.builder()
                .matchLiteral("{fish}").replacement(itemName).build()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    private void rollBreak() {
        if (random.nextDouble() * 100 >= rod.breakChance()) {
            return;
        }
        ItemStack held = listener.rodInHand(player);
        if (held != null) {
            held.setAmount(held.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendMessage(plugin.message("rod-break"));
        }
    }

    /** 보상 없이 끝낸다. 찌가 사라졌거나 접속 종료, 플러그인 비활성화 때 호출된다. */
    void abort() {
        cleanUp();
    }

    private void cleanUp() {
        if (!isCancelled()) {
            cancel();
        }
        listener.sessionEnded(player);
        if (display != null) {
            display.remove();
        }
        if (hook.isValid()) {
            hook.remove();
        }
        if (player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }
}
