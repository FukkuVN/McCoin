package fukkucoin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FukkuCoinPlugin extends JavaPlugin implements Listener {

    private File npcShopFile;
    private FileConfiguration npcShopConfig;

    private File balanceFile;
    private FileConfiguration balanceConfig;

    @Override
    public void onEnable() {
        // Load NPC shop config
        npcShopFile = new File(getDataFolder(), "npcshops.yml");
        if (!npcShopFile.exists()) {
            saveResource("npcshops.yml", false);
        }
        npcShopConfig = YamlConfiguration.loadConfiguration(npcShopFile);

        // Load balance config
        balanceFile = new File(getDataFolder(), "balance.yml");
        if (!balanceFile.exists()) {
            try {
                balanceFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        balanceConfig = YamlConfiguration.loadConfiguration(balanceFile);

        // Save default config (currency name)
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("FukkuCoinPlugin enabled!");
    }

    @Override
    public void onDisable() {
        saveBalances();
        getLogger().info("FukkuCoinPlugin disabled!");
    }

    private void saveBalances() {
        try {
            balanceConfig.save(balanceFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lấy số dư McCoin của người chơi
    public int getBalance(UUID uuid) {
        return balanceConfig.getInt(uuid.toString(), 0);
    }

    // Cộng tiền cho người chơi
    public void addBalance(UUID uuid, int amount) {
        int current = getBalance(uuid);
        balanceConfig.set(uuid.toString(), current + amount);
        saveBalances();
    }

    // Trừ tiền người chơi, trả về true nếu đủ tiền và trừ thành công
    public boolean subtractBalance(UUID uuid, int amount) {
        int current = getBalance(uuid);
        if (current < amount) return false;
        balanceConfig.set(uuid.toString(), current - amount);
        saveBalances();
        return true;
    }

    // Mở shop GUI cho người chơi với NPC name
    private void openShopGUI(Player player, String npcName) {
        if (!npcShopConfig.contains("shops." + npcName + ".items")) {
            player.sendMessage(ChatColor.RED + "NPC này không có shop.");
            return;
        }
        List<Map<?, ?>> items = npcShopConfig.getMapList("shops." + npcName + ".items");

        Inventory inv = Bukkit.createInventory(null, 27, "Shop của " + npcName);

        // Hiển thị số dư McCoin người chơi slot 0
        int balance = getBalance(player.getUniqueId());
        ItemStack balanceItem = new ItemStack(Material.PAPER);
        ItemMeta balanceMeta = balanceItem.getItemMeta();
        balanceMeta.setDisplayName(ChatColor.GOLD + "Số dư McCoin của bạn: " + ChatColor.GREEN + balance);
        balanceItem.setItemMeta(balanceMeta);
        inv.setItem(0, balanceItem);

        // Bắt đầu từ slot 1 cho items shop
        for (int i = 0; i < items.size(); i++) {
            Map<?, ?> itemData = items.get(i);
            String matStr = (String) itemData.get("material");
            Material mat = Material.getMaterial(matStr);
            if (mat == null) continue;

            int amount = (int) itemData.get("amount");
            int price = (int) itemData.get("price");

            ItemStack item = new ItemStack(mat, amount);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.WHITE + mat.name());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Giá: " + ChatColor.YELLOW + price + " " + getConfig().getString("currency"));
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(i + 1, item);
        }
        player.openInventory(inv);
    }

    // Xử lý click NPC để mở shop (cần đặt tên NPC đúng với config)
    @EventHandler
    public void onNPCClick(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getRightClicked() != null && event.getRightClicked().getCustomName() != null) {
            String npcName = event.getRightClicked().getCustomName();
            if (npcShopConfig.contains("shops." + npcName)) {
                openShopGUI(player, npcName);
                event.setCancelled(true);
            }
        }
    }

    // Xử lý click trong inventory shop
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("Shop của ")) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();

            // Không cho click slot 0 (hiển thị số dư)
            if (event.getRawSlot() == 0) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;

            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) return;

            String priceLine = lore.get(0);
            int price = extractPrice(priceLine);
            if (price < 0) {
                player.sendMessage(ChatColor.RED + "Lỗi giá bán!");
                return;
            }

            if (!subtractBalance(player.getUniqueId(), price)) {
                player.sendMessage(ChatColor.RED + "Bạn không đủ " + getConfig().getString("currency") + " để mua món này!");
                return;
            }

            player.getInventory().addItem(new ItemStack(clicked.getType(), clicked.getAmount()));
            player.sendMessage(ChatColor.GREEN + "Bạn đã mua " + clicked.getAmount() + " " + clicked.getType().name() + " với giá " + price + " " + getConfig().getString("currency") + ".");
            player.closeInventory();
        }
    }

    // Hàm trích giá từ lore dạng "Giá: 100 McCoin"
    private int extractPrice(String loreLine) {
        try {
            String[] parts = loreLine.split(" ");
            for (String part : parts) {
                if (part.matches("\\d+")) {
                    return Integer.parseInt(part);
                }
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }
}
