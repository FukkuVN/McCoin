package fukkucoin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class FukkuCoinPlugin extends JavaPlugin implements Listener {

    private File npcShopsFile;
    private FileConfiguration npcShopsConfig;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().severe("Plugin Citizens is required!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        // Load or create npcshops.yml
        npcShopsFile = new File(getDataFolder(), "npcshops.yml");
        if (!npcShopsFile.exists()) {
            saveResource("npcshops.yml", false);
        }
        npcShopsConfig = YamlConfiguration.loadConfiguration(npcShopsFile);

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Chỉ người chơi mới được sử dụng lệnh này.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length >= 2 && args[0].equalsIgnoreCase("npcspawn")) {
            if (!player.hasPermission("fukkucoin.op")) {
                player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này.");
                return true;
            }
            String npcName = args[1];
            spawnNPC(player, npcName);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Sử dụng: /fukkucoin npcspawn <tên_npc>");
        return true;
    }

    private void spawnNPC(Player player, String npcName) {
        Location loc = player.getLocation();
        NPCRegistry registry = CitizensAPI.getNPCRegistry();

        NPC npc = registry.createNPC(org.bukkit.entity.EntityType.VILLAGER, npcName);
        npc.spawn(loc);

        // Lưu npcId vào config npcshops.yml (ví dụ đặt shop tên npcName)
        npcShopsConfig.set("shops." + npcName + ".npcId", npc.getId());
        try {
            npcShopsConfig.save(npcShopsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        player.sendMessage(ChatColor.GREEN + "Đã tạo NPC shop: " + npcName);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith(ChatColor.BLUE + "Shop FukkuCoin")) {
            event.setCancelled(true); // Ngăn người chơi lấy item
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            ItemMeta meta = clicked.getItemMeta();

            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                // Ví dụ kiểm tra lore có chứa giá tiền
                String priceLine = lore.stream().filter(s -> s.contains("Giá:")).findFirst().orElse(null);
                if (priceLine != null) {
                    player.sendMessage(ChatColor.GOLD + "Bạn vừa xem mặt hàng có " + priceLine);
                    // Xử lý logic mua bán tại đây (kiểm tra tiền, trừ tiền, trao vật phẩm...)
                }
            }
        }
    }

    // Mở GUI shop khi người chơi phải tương tác với NPC (cái này bạn tự mở thêm event NPC click, ví dụ Citizens NPCClickEvent)
    public void openShop(Player player, String shopName) {
        List<?> items = getConfig().getList("npcShops." + shopName + ".items");
        if (items == null) {
            player.sendMessage(ChatColor.RED + "Shop không tồn tại hoặc chưa cấu hình.");
            return;
        }

        // Tạo inventory size chuẩn multiples of 9, ví dụ 9 slots
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.BLUE + "Shop FukkuCoin");

        for (Object o : items) {
            if (!(o instanceof java.util.Map)) continue;

            java.util.Map<?, ?> itemMap = (java.util.Map<?, ?>) o;
            String materialName = (String) itemMap.get("material");
            int price = (int) itemMap.get("price");
            int slot = (int) itemMap.get("slot");

            Material mat = Material.getMaterial(materialName);
            if (mat == null) continue;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + mat.name());
                meta.setLore(List.of(ChatColor.YELLOW + "Giá: " + price + " FukkuCoin"));
                item.setItemMeta(meta);
            }

            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item);
            }
        }
        player.openInventory(inv);
    }
}
