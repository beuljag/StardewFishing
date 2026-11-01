package kr.server.stardewfishing;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * ItemsAdder 연동. ItemsAdder가 없는 서버에서도 로드되도록 API를 리플렉션으로 호출한다.
 */
final class ItemsAdderHook {

    private static final String CUSTOM_STACK = "dev.lone.itemsadder.api.CustomStack";

    private ItemsAdderHook() {
    }

    static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /** "namespace:id"에 해당하는 아이템을 만든다. 없는 ID이거나 ItemsAdder가 없으면 null. */
    static ItemStack create(String namespacedId) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Class<?> type = Class.forName(CUSTOM_STACK);
            Object custom = type.getMethod("getInstance", String.class).invoke(null, namespacedId);
            return custom == null ? null : (ItemStack) type.getMethod("getItemStack").invoke(custom);
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning("[StardewFishing] ItemsAdder API 호출 실패: " + e);
            return null;
        }
    }

    /** 아이템의 ItemsAdder ID를 돌려준다. ItemsAdder 아이템이 아니면 null. */
    static String idOf(ItemStack stack) {
        if (!isAvailable() || stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            Class<?> type = Class.forName(CUSTOM_STACK);
            Method byItemStack = type.getMethod("byItemStack", ItemStack.class);
            Object custom = byItemStack.invoke(null, stack);
            return custom == null ? null : (String) type.getMethod("getNamespacedID").invoke(custom);
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning("[StardewFishing] ItemsAdder API 호출 실패: " + e);
            return null;
        }
    }
}
