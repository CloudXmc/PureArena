package com.mcxyd.purearena.item;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.message.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 返回大厅按钮物品：创建、识别（PDC 标记）、发放。
 * 发放与移除必须在玩家实体所有者上下文调用。
 */
public final class BackButtonManager {

    private final NamespacedKey markerKey;
    private final ConfigManager configManager;

    public BackButtonManager(Plugin plugin, ConfigManager configManager) {
        this.markerKey = new NamespacedKey(plugin, "back_button");
        this.configManager = configManager;
    }

    /** 按当前配置构建按钮物品。 */
    public ItemStack build() {
        PluginConfig config = configManager.current();
        // 配置层已校验过材质名，这里再兜底一次防御外部改动
        Material material = Material.matchMaterial(config.backButtonMaterialName());
        if (material == null || !material.isItem()) {
            material = Material.SLIME_BALL;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Texts.parseItemText(config.backButtonName()));
        List<Component> lore = new ArrayList<>();
        for (String line : config.backButtonLore()) {
            lore.add(Texts.parseItemText(line));
        }
        meta.lore(lore);
        if (config.backButtonCustomModelData() > 0) {
            meta.setCustomModelData(config.backButtonCustomModelData());
        }
        // PDC 标记用于识别，材质改变后旧按钮依然可识别
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** 判断物品是否为返回按钮，依据 PDC 标记而不是材质。 */
    public boolean isBackButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * 发放（或按配置刷新/移除）按钮。
     * 先清除已有按钮再放置，防止槽位修改后出现两个按钮。
     */
    public void give(Player player) {
        PluginConfig config = configManager.current();
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isBackButton(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
        if (config.backButtonVisible()) {
            inventory.setItem(config.backButtonInventoryIndex(), build());
        }
    }
}
