package com.mcxyd.purearena.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 只负责删除旧版本发放且带插件标记的竞技场木剑，不创建或锁定任何物品。 */
public final class LegacyArenaSwordCleaner {

    private final NamespacedKey legacyMarkerKey;

    public LegacyArenaSwordCleaner(Plugin plugin) {
        this.legacyMarkerKey = new NamespacedKey(plugin, "arena_sword");
    }

    public void removeFrom(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isLegacyArenaSword(item)) {
                inventory.setItem(slot, null);
            }
        }
    }

    public boolean isLegacyArenaSword(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(legacyMarkerKey, PersistentDataType.BYTE);
    }
}
