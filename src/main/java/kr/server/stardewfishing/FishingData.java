package kr.server.stardewfishing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** rods.yml, fish.yml, zones.yml을 읽고 쓴다. */
final class FishingData {

    record Rod(String id, ItemSpec item, double barSizePercent, int biteMin, int biteMax, double breakChance) {
        float barHeight() {
            return FishingGame.TRACK * (float) barSizePercent / 100f;
        }
    }

    record Fish(String id, ItemSpec item, float difficulty, FishingGame.Motion motion, int exp) {
    }

    record Zone(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                Map<String, Double> fish) {

        boolean contains(Location loc) {
            return loc.getWorld() != null && loc.getWorld().getName().equals(world)
                    && loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private final StardewFishingPlugin plugin;
    private final NamespacedKey rodKey;
    private final Map<String, Rod> rods = new LinkedHashMap<>();
    private final Map<String, Fish> fish = new LinkedHashMap<>();
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    FishingData(StardewFishingPlugin plugin) {
        this.plugin = plugin;
        this.rodKey = new NamespacedKey(plugin, "rod");
    }

    void load() {
        rods.clear();
        fish.clear();
        zones.clear();

        ConfigurationSection rodRoot = read("rods.yml").getConfigurationSection("rods");
        if (rodRoot != null) {
            for (String id : rodRoot.getKeys(false)) {
                rods.put(id, parseRod(id, rodRoot.getConfigurationSection(id)));
            }
        }

        ConfigurationSection fishRoot = read("fish.yml").getConfigurationSection("fish");
        if (fishRoot != null) {
            for (String id : fishRoot.getKeys(false)) {
                fish.put(id, parseFish(id, fishRoot.getConfigurationSection(id)));
            }
        }

        ConfigurationSection zoneRoot = read("zones.yml").getConfigurationSection("zones");
        if (zoneRoot != null) {
            for (String id : zoneRoot.getKeys(false)) {
                zones.put(id, parseZone(id, zoneRoot.getConfigurationSection(id)));
            }
        }

        warnAboutItemsAdder();
    }

    private YamlConfiguration read(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private Rod parseRod(String id, ConfigurationSection section) {
        int min = Math.max(0, section.getInt("bite-time.min", 100));
        int max = Math.max(min, section.getInt("bite-time.max", 600));
        return new Rod(id,
                ItemSpec.parse(section.getConfigurationSection("item"), Material.FISHING_ROD),
                clamp(section.getDouble("bar-size", 25), 5, 100),
                min, max,
                clamp(section.getDouble("break-chance", 0), 0, 100));
    }

    private Fish parseFish(String id, ConfigurationSection section) {
        FishingGame.Motion motion = FishingGame.Motion.MIXED;
        String motionName = section.getString("motion", "MIXED");
        try {
            motion = FishingGame.Motion.valueOf(motionName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("물고기 " + id + "의 motion이 잘못되었습니다: " + motionName + " (MIXED로 처리)");
        }
        return new Fish(id,
                ItemSpec.parse(section.getConfigurationSection("item"), Material.COD),
                (float) clamp(section.getDouble("difficulty", 30), 1, 150),
                motion,
                Math.max(0, section.getInt("exp", 0)));
    }

    private Zone parseZone(String id, ConfigurationSection section) {
        Map<String, Double> table = new LinkedHashMap<>();
        ConfigurationSection fishSection = section.getConfigurationSection("fish");
        if (fishSection != null) {
            for (String fishId : fishSection.getKeys(false)) {
                double weight = fishSection.getDouble(fishId);
                if (!fish.containsKey(fishId)) {
                    plugin.getLogger().warning("낚시터 " + id + ": fish.yml에 없는 물고기 " + fishId + " (무시됨)");
                } else if (weight > 0) {
                    table.put(fishId, weight);
                }
            }
        }
        return new Zone(id, section.getString("world", "world"),
                section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"),
                table);
    }

    private void warnAboutItemsAdder() {
        if (ItemsAdderHook.isAvailable()) {
            return;
        }
        rods.values().stream().filter(r -> r.item().itemsAdderId() != null).forEach(r ->
                plugin.getLogger().warning("낚시대 " + r.id() + "는 ItemsAdder 아이템인데 ItemsAdder가 없습니다."));
        fish.values().stream().filter(f -> f.item().itemsAdderId() != null).forEach(f ->
                plugin.getLogger().warning("물고기 " + f.id() + "는 ItemsAdder 아이템인데 ItemsAdder가 없습니다."));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    Map<String, Rod> rods() {
        return rods;
    }

    Map<String, Fish> fish() {
        return fish;
    }

    Map<String, Zone> zones() {
        return zones;
    }

    /** 겹치는 낚시터가 있으면 가장 작은 곳을 고른다. 큰 호수 안에 희귀 포인트를 둘 수 있다. */
    Zone zoneAt(Location location) {
        return zones.values().stream()
                .filter(zone -> zone.contains(location))
                .min(Comparator.comparingLong(Zone::volume))
                .orElse(null);
    }

    Fish pickFish(Zone zone, Random random) {
        double total = zone.fish().values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = random.nextDouble() * total;
        Fish picked = null;
        for (Map.Entry<String, Double> entry : zone.fish().entrySet()) {
            picked = fish.get(entry.getKey());
            roll -= entry.getValue();
            if (roll < 0) {
                break;
            }
        }
        return picked;
    }

    /** 플러그인 태그나 ItemsAdder ID로 낚시대를 찾는다. 커스텀 낚시대가 아니면 null. */
    Rod rodOf(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(rodKey, PersistentDataType.STRING);
        if (id != null) {
            return rods.get(id);
        }
        String itemsAdderId = ItemsAdderHook.idOf(item);
        if (itemsAdderId != null) {
            for (Rod rod : rods.values()) {
                if (itemsAdderId.equals(rod.item().itemsAdderId())) {
                    return rod;
                }
            }
        }
        return null;
    }

    /** 지급용 낚시대를 만든다. 내구도는 파괴 확률로만 관리하므로 부서지지 않게 설정한다. */
    ItemStack buildRod(Rod rod) {
        ItemStack item = rod.item().build();
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(rodKey, PersistentDataType.STRING, rod.id());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    void saveZone(Zone zone) throws IOException {
        File file = new File(plugin.getDataFolder(), "zones.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "zones." + zone.id();
        yaml.set(path, null);
        yaml.set(path + ".world", zone.world());
        yaml.set(path + ".min.x", zone.minX());
        yaml.set(path + ".min.y", zone.minY());
        yaml.set(path + ".min.z", zone.minZ());
        yaml.set(path + ".max.x", zone.maxX());
        yaml.set(path + ".max.y", zone.maxY());
        yaml.set(path + ".max.z", zone.maxZ());
        zone.fish().forEach((fishId, weight) -> yaml.set(path + ".fish." + fishId, weight));
        yaml.save(file);
        zones.put(zone.id(), zone);
    }

    void deleteZone(String id) throws IOException {
        File file = new File(plugin.getDataFolder(), "zones.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("zones." + id, null);
        yaml.save(file);
        zones.remove(id);
    }

    /** 손에 든 아이템을 물고기로 등록한다. ItemsAdder 아이템은 ID로 저장해 원본 변경을 따라가게 한다. */
    Fish saveFishFromItem(String id, ItemStack held, float difficulty, FishingGame.Motion motion, int exp)
            throws IOException {
        File file = new File(plugin.getDataFolder(), "fish.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "fish." + id;
        yaml.set(path, null);
        yaml.set(path + ".difficulty", (double) difficulty);
        yaml.set(path + ".motion", motion.name());
        yaml.set(path + ".exp", exp);

        String itemsAdderId = ItemsAdderHook.idOf(held);
        ItemStack single = held.clone();
        single.setAmount(1);
        if (itemsAdderId != null) {
            yaml.set(path + ".item.itemsadder", itemsAdderId);
        } else {
            yaml.set(path + ".item.stack", single);
        }
        yaml.save(file);

        Fish saved = new Fish(id, new ItemSpec(itemsAdderId, single.getType(), itemsAdderId == null ? single : null,
                null, List.of(), null), difficulty, motion, exp);
        fish.put(id, saved);
        return saved;
    }
}
