package kr.server.stardewfishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 설정 파일의 item 항목. ItemsAdder ID, 바닐라 재료, 저장된 아이템 중 하나로 만들어진다.
 * ItemsAdder는 서버 시작 후 비동기로 로드되므로 아이템은 필요할 때마다 만든다.
 */
record ItemSpec(String itemsAdderId, Material material, ItemStack stack,
                String name, List<String> lore, Integer customModelData) {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    static Component color(String text) {
        return LEGACY.deserialize(text);
    }

    static ItemSpec parse(ConfigurationSection section, Material fallback) {
        if (section == null) {
            return new ItemSpec(null, fallback, null, null, List.of(), null);
        }
        Material material = Material.matchMaterial(section.getString("material", fallback.name()));
        return new ItemSpec(
                section.getString("itemsadder"),
                material != null ? material : fallback,
                section.getItemStack("stack"),
                section.getString("name"),
                section.getStringList("lore"),
                section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null);
    }

    /** 아이템을 만든다. ItemsAdder ID를 찾지 못하면 null. */
    ItemStack build() {
        ItemStack item;
        if (itemsAdderId != null) {
            item = ItemsAdderHook.create(itemsAdderId);
            if (item == null) {
                return null;
            }
        } else if (stack != null) {
            item = stack.clone();
        } else {
            item = new ItemStack(material);
        }

        if (name != null || !lore.isEmpty() || customModelData != null) {
            ItemMeta meta = item.getItemMeta();
            if (name != null) {
                meta.displayName(plain(name));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(ItemSpec::plain).toList());
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component plain(String text) {
        return color(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
