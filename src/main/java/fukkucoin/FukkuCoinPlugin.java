package fukkucoin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class FukkuCoinPlugin extends JavaPlugin implements TabExecutor {

    private Map<UUID, Integer> playerBalances = new HashMap<>();
    private Set<UUID> checkedInToday = new HashSet<>();

    // NPC vị trí giả lập
    private Location npcLocation = null;

    // Cửa hàng đơn giản: danh sách item bán (Material + giá)
    private Map<Material, Integer> shopItems = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("FukkuCoinPlugin enabled!");
        this.getCommand("fukkucoin").setExecutor(this);

        // Khởi tạo cửa hàng ví dụ
        shopItems.put(Material.DIAMOND, 100);
        shopItems.put(Material.GOLD_INGOT, 50);
        shopItems.put(Material.IRON_INGOT, 20);
    }

    @Override
    public void onDisable() {
        getLogger().info("FukkuCoinPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Chỉ người chơi mới được dùng lệnh.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        // Các lệnh người chơi thường
        switch (args[0].toLowerCase()) {
            case "balance":
                showBalance(player);
                return true;
            case "shop":
                openShop(player);
                return true;
            case "checkin":
                checkIn(player);
                return true;
        }

        // Lệnh OP có permission: fukkucoin.admin
        if (!player.hasPermission("fukkucoin.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền dùng lệnh này.");
            return true;
        }

        // Lệnh quản trị
        switch (args[0].toLowerCase()) {
            case "npcspawn":
                spawnNPC(player);
                return true;
            case "npcmove":
                moveNPC(player);
                return true;
            case "additem":
                addItemShop(player, args);
                return true;
            case "removeitem":
                removeItemShop(player, args);
                return true;
            case "addcoin":
                addCoin(player, args);
                return true;
            case "removecoin":
                removeCoin(player, args);
                return true;
            case "reload":
                reloadPlugin(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "Lệnh không hợp lệ.");
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== FukkuCoin Plugin ===");
        player.sendMessage(ChatColor.YELLOW + "/fukkucoin balance - Xem số dư");
        player.sendMessage(ChatColor.YELLOW + "/fukkucoin shop - Mở cửa hàng");
        player.sendMessage(ChatColor.YELLOW + "/fukkucoin checkin - Điểm danh nhận coin");
        if (player.hasPermission("fukkucoin.admin")) {
            player.sendMessage(ChatColor.AQUA + "=== Lệnh quản trị ===");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin npcspawn - Tạo NPC tại vị trí hiện tại");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin npcmove - Di chuyển NPC đến vị trí hiện tại");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin additem <material> <giá> - Thêm item cửa hàng");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin removeitem <material> - Xóa item cửa hàng");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin addcoin <player> <số> - Cộng coin cho người chơi");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin removecoin <player> <số> - Trừ coin người chơi");
            player.sendMessage(ChatColor.AQUA + "/fukkucoin reload - Reload plugin");
        }
    }

    private void showBalance(Player player) {
        int bal = playerBalances.getOrDefault(player.getUniqueId(), 0);
        player.sendMessage(ChatColor.GOLD + "Số dư của bạn: " + ChatColor.GREEN + bal + " coin");
    }

    private void openShop(Player player) {
        int balance = playerBalances.getOrDefault(player.getUniqueId(), 0);
        Inventory shopInv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "FukkuCoin Shop - Số dư: " + balance + " coin");

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : shopItems.entrySet()) {
            ItemStack item = new ItemStack(entry.getKey(), 1);
            shopInv.setItem(slot++, item);
        }

        player.openInventory(shopInv);
    }

    private void checkIn(Player player) {
        if (checkedInToday.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Bạn đã điểm danh hôm nay rồi!");
            return;
        }
        checkedInToday.add(player.getUniqueId());
        int current = playerBalances.getOrDefault(player.getUniqueId(), 0);
        int reward = 50;
        playerBalances.put(player.getUniqueId(), current + reward);
        player.sendMessage(ChatColor.GREEN + "Điểm danh thành công! Bạn nhận được " + reward + " coin.");
    }

    private void spawnNPC(Player player) {
        npcLocation = player.getLocation();
        player.sendMessage(ChatColor.AQUA + "NPC đã được tạo tại vị trí của bạn: " + formatLocation(npcLocation));
    }

    private void moveNPC(Player player) {
        if (npcLocation == null) {
            player.sendMessage(ChatColor.RED + "Chưa có NPC nào được tạo. Dùng /fukkucoin npcspawn để tạo NPC.");
            return;
        }
        npcLocation = player.getLocation();
        player.sendMessage(ChatColor.AQUA + "NPC đã di chuyển đến vị trí mới: " + formatLocation(npcLocation));
    }

    private void addItemShop(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Cách dùng: /fukkucoin additem <material> <giá>");
            return;
        }
        Material mat = Material.matchMaterial(args[1]);
        if (mat == null) {
            player.sendMessage(ChatColor.RED + "Material không hợp lệ.");
            return;
        }
        int price;
        try {
            price = Integer.parseInt(args[2]);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Giá phải là số nguyên dương.");
            return;
        }

        shopItems.put(mat, price);
        player.sendMessage(ChatColor.GREEN + "Đã thêm " + mat.name() + " vào cửa hàng với giá " + price + " coin.");
    }

    private void removeItemShop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Cách dùng: /fukkucoin removeitem <material>");
            return;
        }
        Material mat = Material.matchMaterial(args[1]);
        if (mat == null) {
            player.sendMessage(ChatColor.RED + "Material không hợp lệ.");
            return;
        }

        if (shopItems.remove(mat) != null) {
            player.sendMessage(ChatColor.GREEN + "Đã xóa " + mat.name() + " khỏi cửa hàng.");
        } else {
            player.sendMessage(ChatColor.RED + "Item không tồn tại trong cửa hàng.");
        }
    }

    private void addCoin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Cách dùng: /fukkucoin addcoin <player> <số>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Người chơi không online hoặc không tồn tại.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Số coin phải là số nguyên dương.");
            return;
        }

        UUID targetId = target.getUniqueId();
        int current = playerBalances.getOrDefault(targetId, 0);
        playerBalances.put(targetId, current + amount);

        player.sendMessage(ChatColor.GREEN + "Đã cộng " + amount + " coin cho " + target.getName());
        target.sendMessage(ChatColor.GREEN + "Bạn được cộng " + amount + " coin bởi " + player.getName());
    }

    private void removeCoin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Cách dùng: /fukkucoin removecoin <player> <số>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Người chơi không online hoặc không tồn tại.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Số coin phải là số nguyên dương.");
            return;
        }

        UUID targetId = target.getUniqueId();
        int current = playerBalances.getOrDefault(targetId, 0);
        int newAmount = current - amount;
        if (newAmount < 0) newAmount = 0;
        playerBalances.put(targetId, newAmount);

        player.sendMessage(ChatColor.GREEN + "Đã trừ " + amount + " coin của " + target.getName());
        target.sendMessage(ChatColor.RED + "Bạn bị trừ " + amount + " coin bởi " + player.getName());
    }

    private void reloadPlugin(Player player) {
        // Nếu có config thì reload config ở đây
        player.sendMessage(ChatColor.GREEN + "Plugin đã reload (giả lập).");
    }

    private String formatLocation(Location loc) {
        return String.format("X=%.1f Y=%.1f Z=%.1f", loc.getX(), loc.getY(), loc.getZ());
    }
}
