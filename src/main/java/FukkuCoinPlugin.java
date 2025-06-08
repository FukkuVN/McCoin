package fukkucoin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FukkuCoinPlugin extends JavaPlugin {

    private CoinManager coinManager;
    private DailyRewardManager dailyRewardManager;
    private NPCShopManager npcShopManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("npcshops.yml", false);

        coinManager = new CoinManager();
        dailyRewardManager = new DailyRewardManager();
        npcShopManager = new NPCShopManager();

        getCommand("fcbalance").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            int balance = coinManager.getBalance(player.getUniqueId());
            player.sendMessage("§aSố dư FukkuCoin của bạn: §e" + balance + " FC");
            return true;
        });

        getCommand("fcclaim").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (dailyRewardManager.canClaim(player.getUniqueId())) {
                int reward = getConfig().getInt("daily-reward");
                coinManager.addCoins(player.getUniqueId(), reward);
                dailyRewardManager.markClaimed(player.getUniqueId());
                player.sendMessage("§aBạn đã nhận §e" + reward + " FC §atừ phần thưởng hằng ngày!");
            } else {
                player.sendMessage("§cBạn đã nhận hôm nay rồi. Hãy quay lại ngày mai!");
            }
            return true;
        });

        getCommand("fcnpc").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission("fukkucoin.admin")) return true;

            if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
                String npcName = args[1];
                npcShopManager.openShop(player, npcName);
                player.sendMessage("§aMở shop cho NPC tên §e" + npcName);
            } else {
                player.sendMessage("§cUsage: /fcnpc create <npcName>");
            }
            return true;
        });
    }

    // CoinManager (inner class)
    public class CoinManager {
        private final ConcurrentHashMap<UUID, Integer> balances = new ConcurrentHashMap<>();

        public int getBalance(UUID uuid) {
            return balances.getOrDefault(uuid, 0);
        }

        public void addCoins(UUID uuid, int amount) {
            balances.put(uuid, getBalance(uuid) + amount);
        }

        public boolean deductCoins(UUID uuid, int amount) {
            int current = getBalance(uuid);
            if (current >= amount) {
                balances.put(uuid, current - amount);
                return true;
            }
            return false;
        }
    }

    // DailyRewardManager (inner class)
    public class DailyRewardManager {
        private final HashMap<UUID, Long> lastClaimTime = new HashMap<>();

        public boolean canClaim(UUID uuid) {
            long now = System.currentTimeMillis();
            return !lastClaimTime.containsKey(uuid) || now - lastClaimTime.get(uuid) >= 86400000;
        }

        public void markClaimed(UUID uuid) {
            lastClaimTime.put(uuid, System.currentTimeMillis());
        }
    }

    // NPCShopManager (inner class)
    public class NPCShopManager {
        private final YamlConfiguration shopConfig;

        public NPCShopManager() {
            File file = new File(getDataFolder(), "npcshops.yml");
            this.shopConfig = YamlConfiguration.loadConfiguration(file);
        }

        public void openShop(Player player, String npcName) {
            if (!shopConfig.contains("shops." + npcName)) {
                player.sendMessage("§cKhông tìm thấy shop cho NPC tên §e" + npcName);
                return;
            }
            List<?> items = shopConfig.getList("shops." + npcName);
            ShopGUI.openShop(player, items);
        }
    }

    // ShopGUI (static inner class)
    public static class ShopGUI {
        public static void openShop(FukkuCoinPlugin plugin, Player player, List<?> items) {
            Inventory inv = Bukkit.createInventory(null, 27, "§6Shop FukkuCoin");

            for (Object obj : items) {
                if (obj instanceof Map) {
                    Map<String, Object> itemData = (Map<String, Object>) obj;
                    Material material = Material.valueOf((String) itemData.get("material"));
                    int amount = (int) itemData.get("amount");
                    int price = (int) itemData.get("price");

                    ItemStack itemStack = new ItemStack(material, amount);
                    ItemMeta meta = itemStack.getItemMeta();
                    meta.setDisplayName("§a" + material.name());
                    meta.setLore(List.of("§eGiá: " + price + " FC", "§7Click để mua"));
                    itemStack.setItemMeta(meta);

                    inv.addItem(itemStack);
                }
            }

            player.openInventory(inv);

            Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onClick(InventoryClickEvent e) {
                    if (!e.getInventory().equals(inv)) return;
                    if (e.getCurrentItem() == null) return;
                    if (!(e.getWhoClicked() instanceof Player)) return;

                    Player p = (Player) e.getWhoClicked();
                    e.setCancelled(true);

                    ItemStack clicked = e.getCurrentItem();
                    List<String> lore = clicked.getItemMeta().getLore();
                    if (lore == null || lore.isEmpty()) return;

                    String priceLine = lore.get(0).replace("§eGiá: ", "").replace(" FC", "");
                    int price = Integer.parseInt(priceLine);

                    FukkuCoinPlugin pluginInstance = (FukkuCoinPlugin) Bukkit.getPluginManager().getPlugin("FukkuCoin");
                    if (pluginInstance.coinManager.deductCoins(p.getUniqueId(), price)) {
                        p.getInventory().addItem(new ItemStack(clicked.getType(), clicked.getAmount()));
                        p.sendMessage("§aMua thành công §e" + clicked.getAmount() + " " + clicked.getType() + " §avới giá §e" + price + " FC");
                    } else {
                        p.sendMessage("§cBạn không đủ FukkuCoin!");
                    }
                }
            }, plugin);
        }
    }
}
