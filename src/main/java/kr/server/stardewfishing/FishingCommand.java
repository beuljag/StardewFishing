package kr.server.stardewfishing;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class FishingCommand implements TabExecutor {

    private static final String PREFIX = "&b[낚시] &f";

    private final StardewFishingPlugin plugin;
    private final FishingData data;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    FishingCommand(StardewFishingPlugin plugin, FishingData data) {
        this.plugin = plugin;
        this.data = data;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "pos1" -> setPos(sender, pos1, "1");
                case "pos2" -> setPos(sender, pos2, "2");
                case "zone" -> zone(sender, args);
                case "rod" -> rod(sender, args);
                case "fish" -> fish(sender, args);
                case "reload" -> {
                    plugin.reload();
                    send(sender, "설정을 다시 불러왔습니다.");
                }
                default -> help(sender, label);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("파일 저장 실패: " + e);
            send(sender, "&c파일 저장에 실패했습니다. 콘솔을 확인하세요.");
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "명령어 목록");
        for (String line : List.of(
                "pos1 | pos2 &7- 바라보는 블록(물 포함)을 구역 모서리로 지정",
                "zone create <낚시터> &7- 지정한 구역으로 낚시터 생성",
                "zone delete <낚시터> &7- 낚시터 삭제",
                "zone addfish <낚시터> <물고기> <가중치> &7- 물고기 추가",
                "zone removefish <낚시터> <물고기> &7- 물고기 제거",
                "zone list | zone info <낚시터> &7- 낚시터 조회",
                "rod list | rod give <낚시대> [플레이어] &7- 낚시대 조회/지급",
                "fish list | fish give <물고기> [플레이어] &7- 물고기 조회/지급",
                "fish add <ID> <난이도> <움직임> [경험치] &7- 손에 든 아이템을 물고기로 등록",
                "reload &7- 설정 다시 불러오기")) {
            sender.sendMessage(ItemSpec.color("&e/" + label + " " + line));
        }
    }

    private void setPos(CommandSender sender, Map<UUID, Location> target, String number) {
        if (!(sender instanceof Player player)) {
            send(sender, "&c플레이어만 사용할 수 있습니다.");
            return;
        }
        Block block = player.getTargetBlockExact(50, FluidCollisionMode.ALWAYS);
        Location location = block != null ? block.getLocation() : player.getLocation().getBlock().getLocation();
        target.put(player.getUniqueId(), location);
        send(sender, "위치 " + number + " 지정: &e" + location.getBlockX() + ", " + location.getBlockY()
                + ", " + location.getBlockZ());
    }

    private void zone(CommandSender sender, String[] args) throws IOException {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "list" -> send(sender, "낚시터: &e" + String.join(", ", data.zones().keySet()));
            case "create" -> {
                if (requireArgs(sender, args, 3, "zone create <낚시터>")) {
                    createZone(sender, args[2]);
                }
            }
            case "delete" -> {
                if (requireArgs(sender, args, 3, "zone delete <낚시터>") && findZone(sender, args[2]) != null) {
                    data.deleteZone(args[2]);
                    send(sender, "낚시터 &e" + args[2] + "&f 삭제 완료.");
                }
            }
            case "info" -> {
                if (requireArgs(sender, args, 3, "zone info <낚시터>")) {
                    zoneInfo(sender, args[2]);
                }
            }
            case "addfish" -> {
                if (requireArgs(sender, args, 5, "zone addfish <낚시터> <물고기> <가중치>")) {
                    addFish(sender, args[2], args[3], args[4]);
                }
            }
            case "removefish" -> {
                if (requireArgs(sender, args, 4, "zone removefish <낚시터> <물고기>")) {
                    removeFish(sender, args[2], args[3]);
                }
            }
            default -> send(sender, "&c/낚시 zone <create|delete|list|info|addfish|removefish>");
        }
    }

    private void createZone(CommandSender sender, String id) throws IOException {
        if (!(sender instanceof Player player)) {
            send(sender, "&c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (!isValidId(sender, id)) {
            return;
        }
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null) {
            send(sender, "&c먼저 pos1, pos2로 구역을 지정하세요.");
            return;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            send(sender, "&cpos1과 pos2가 서로 다른 월드에 있습니다.");
            return;
        }
        FishingData.Zone old = data.zones().get(id);
        Map<String, Double> table = old != null ? old.fish() : new LinkedHashMap<>();
        data.saveZone(new FishingData.Zone(id, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()),
                table));
        send(sender, "낚시터 &e" + id + "&f 저장 완료. 이제 물고기를 추가하세요.");
    }

    private void zoneInfo(CommandSender sender, String id) {
        FishingData.Zone zone = findZone(sender, id);
        if (zone == null) {
            return;
        }
        send(sender, "&e" + id + "&f (" + zone.world() + ") "
                + zone.minX() + ", " + zone.minY() + ", " + zone.minZ() + " ~ "
                + zone.maxX() + ", " + zone.maxY() + ", " + zone.maxZ());
        double total = zone.fish().values().stream().mapToDouble(Double::doubleValue).sum();
        zone.fish().forEach((fishId, weight) -> sender.sendMessage(ItemSpec.color(
                "&7- &f" + fishId + " &7가중치 " + weight
                        + String.format(Locale.ROOT, " (%.1f%%)", weight / total * 100))));
    }

    private void addFish(CommandSender sender, String zoneId, String fishId, String weightText) throws IOException {
        FishingData.Zone zone = findZone(sender, zoneId);
        if (zone == null) {
            return;
        }
        if (!data.fish().containsKey(fishId)) {
            send(sender, "&c없는 물고기입니다: " + fishId);
            return;
        }
        Double weight = parsePositive(weightText);
        if (weight == null) {
            send(sender, "&c가중치는 0보다 큰 숫자여야 합니다.");
            return;
        }
        zone.fish().put(fishId, weight);
        data.saveZone(zone);
        send(sender, "낚시터 &e" + zoneId + "&f에 &e" + fishId + "&f 추가 (가중치 " + weight + ")");
    }

    private void removeFish(CommandSender sender, String zoneId, String fishId) throws IOException {
        FishingData.Zone zone = findZone(sender, zoneId);
        if (zone == null) {
            return;
        }
        if (zone.fish().remove(fishId) == null) {
            send(sender, "&c이 낚시터에 없는 물고기입니다: " + fishId);
            return;
        }
        data.saveZone(zone);
        send(sender, "낚시터 &e" + zoneId + "&f에서 &e" + fishId + "&f 제거.");
    }

    private void rod(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("list")) {
            send(sender, "낚시대 목록");
            data.rods().values().forEach(rod -> sender.sendMessage(ItemSpec.color(
                    "&7- &f" + rod.id() + " &7빠 " + rod.barSizePercent() + "% / 입질 " + rod.biteMin() + "~"
                            + rod.biteMax() + "틱 / 파괴 " + rod.breakChance() + "%")));
        } else if (action.equals("give") && requireArgs(sender, args, 3, "rod give <낚시대> [플레이어]")) {
            FishingData.Rod rod = data.rods().get(args[2]);
            if (rod == null) {
                send(sender, "&c없는 낚시대입니다: " + args[2]);
                return;
            }
            ItemStack item = data.buildRod(rod);
            if (item == null) {
                send(sender, "&c아이템을 만들 수 없습니다. ItemsAdder ID를 확인하고, 재료가 FISHING_ROD인지 확인하세요.");
                return;
            }
            give(sender, args, item, rod.id());
        } else if (!action.equals("give")) {
            send(sender, "&c/낚시 rod <list|give>");
        }
    }

    private void fish(CommandSender sender, String[] args) throws IOException {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "list" -> {
                send(sender, "물고기 목록");
                data.fish().values().forEach(fish -> sender.sendMessage(ItemSpec.color(
                        "&7- &f" + fish.id() + " &7난이도 " + fish.difficulty() + " / " + fish.motion())));
            }
            case "give" -> {
                if (requireArgs(sender, args, 3, "fish give <물고기> [플레이어]")) {
                    FishingData.Fish fish = data.fish().get(args[2]);
                    if (fish == null) {
                        send(sender, "&c없는 물고기입니다: " + args[2]);
                        return;
                    }
                    ItemStack item = fish.item().build();
                    if (item == null) {
                        send(sender, "&c아이템을 만들 수 없습니다. ItemsAdder ID를 확인하세요.");
                        return;
                    }
                    give(sender, args, item, fish.id());
                }
            }
            case "add" -> {
                if (requireArgs(sender, args, 5, "fish add <ID> <난이도> <움직임> [경험치]")) {
                    addFishFromHand(sender, args);
                }
            }
            default -> send(sender, "&c/낚시 fish <list|give|add>");
        }
    }

    private void addFishFromHand(CommandSender sender, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            send(sender, "&c플레이어만 사용할 수 있습니다.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            send(sender, "&c등록할 아이템을 손에 드세요.");
            return;
        }
        if (!isValidId(sender, args[2])) {
            return;
        }
        Double difficulty = parsePositive(args[3]);
        if (difficulty == null) {
            send(sender, "&c난이도는 0보다 큰 숫자여야 합니다.");
            return;
        }
        FishingGame.Motion motion;
        try {
            motion = FishingGame.Motion.valueOf(args[4].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "&c움직임은 " + Arrays.toString(FishingGame.Motion.values()) + " 중 하나입니다.");
            return;
        }
        int exp = 0;
        if (args.length > 5) {
            try {
                exp = Math.max(0, Integer.parseInt(args[5]));
            } catch (NumberFormatException e) {
                send(sender, "&c경험치는 정수여야 합니다.");
                return;
            }
        }
        data.saveFishFromItem(args[2], held, difficulty.floatValue(), motion, exp);
        send(sender, "물고기 &e" + args[2] + "&f 등록 완료. zone addfish로 낚시터에 추가하세요.");
    }

    private void give(CommandSender sender, String[] args, ItemStack item, String name) {
        Player target;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
        } else {
            target = sender instanceof Player player ? player : null;
        }
        if (target == null) {
            send(sender, "&c대상 플레이어를 찾을 수 없습니다.");
            return;
        }
        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        send(sender, "&e" + target.getName() + "&f에게 &e" + name + "&f 지급.");
    }

    private FishingData.Zone findZone(CommandSender sender, String id) {
        FishingData.Zone zone = data.zones().get(id);
        if (zone == null) {
            send(sender, "&c없는 낚시터입니다: " + id);
        }
        return zone;
    }

    private boolean isValidId(CommandSender sender, String id) {
        // 점은 YAML 경로 구분자라서 ID에 쓸 수 없다.
        if (id.contains(".")) {
            send(sender, "&cID에는 점(.)을 쓸 수 없습니다.");
            return false;
        }
        return true;
    }

    private Double parsePositive(String text) {
        try {
            double value = Double.parseDouble(text);
            return value > 0 && Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean requireArgs(CommandSender sender, String[] args, int count, String usage) {
        if (args.length < count) {
            send(sender, "&c/낚시 " + usage);
            return false;
        }
        return true;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ItemSpec.color(PREFIX + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return switch (args.length) {
            case 1 -> match(args[0], List.of("pos1", "pos2", "zone", "rod", "fish", "reload"));
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "zone" -> match(args[1], List.of("create", "delete", "list", "info", "addfish", "removefish"));
                case "rod" -> match(args[1], List.of("list", "give"));
                case "fish" -> match(args[1], List.of("list", "give", "add"));
                default -> List.of();
            };
            case 3 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "zone" -> match(args[2], data.zones().keySet());
                case "rod" -> args[1].equalsIgnoreCase("give") ? match(args[2], data.rods().keySet()) : List.of();
                case "fish" -> args[1].equalsIgnoreCase("give") ? match(args[2], data.fish().keySet()) : List.of();
                default -> List.of();
            };
            case 4 -> {
                if (args[0].equalsIgnoreCase("zone")
                        && (args[1].equalsIgnoreCase("addfish") || args[1].equalsIgnoreCase("removefish"))) {
                    yield match(args[3], data.fish().keySet());
                }
                if (args[1].equalsIgnoreCase("give")) {
                    yield match(args[3], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                }
                yield List.of();
            }
            case 5 -> args[0].equalsIgnoreCase("fish") && args[1].equalsIgnoreCase("add")
                    ? match(args[4], Arrays.stream(FishingGame.Motion.values()).map(Enum::name).toList())
                    : List.of();
            default -> List.of();
        };
    }

    private List<String> match(String prefix, Collection<String> options) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(option);
            }
        }
        return result;
    }
}
