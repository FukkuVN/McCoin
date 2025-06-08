package fukkucoin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class FukkuCoinPlugin extends JavaPlugin {

    private File moneyFile;
    private YamlConfiguration moneyConfig;

    private File npcFile;
    private YamlConfiguration npcConfig;

    // Shop items: itemName -> price
    private Map<String, Double> shopItems = new HashMap<>();

    // Cooldown claim daily: uuid -> lastClaimTime (epoch millis)
    private Map<UUID, Long> dailyClaimTime = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Load or create money.yml
        moneyFile = new File(getDataFolder(), "money.yml");
        if (!moneyFile.exists()) saveResource("money.yml", false);
        moneyConfig = YamlConfiguration.loadConfiguration(moneyFile);

        // Load or create npcshops.yml
        npcFile = new File(getDataFolder(), "npcshops.yml");
        if (!npcFile.exists()) saveResource("npcshops.yml", false);
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);

        // Khởi tạo shop items
        shopItems.put("sword", 500.0);
        shopItems.put("shield", 300.0);
        shopItems.put("apple", 10.0);

        // Load daily claim time từ money.yml nếu có
        if (moneyConfig.contains("dailyClaimTime")) {
            for (String key : moneyConfig.getConfigurationSection("dailyClaimTime").getKeys(false)) {
                long time = moneyConfig.getLong("dailyClaimTime." + key);
                try {
                    UUID uuid = UUID.fromString(key);
                    dailyClaimTime.put(uuid, time);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        getLogger().info("FukkuCoinPlugin enabled!");
    }

    @Override
    public void onDisable() {
        // Lưu lại dữ liệu money và dailyClaimTime
        try {
            // Lưu daily claim time vào money.yml
            for (Map.Entry<UUID, Long> entry : dailyClaimTime.entrySet()) {
                moneyConfig.set("dailyClaimTime." + entry.getKey().toString(), entry.getValue());
            }
            moneyConfig.save(moneyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        getLogger().info("FukkuCoinPlugin disabled!");
    }

    // --- Lệnh chính ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("fukkucoin")) return false;

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (!(sender instanceof Player) && !sub.equals("reload")) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Player p = (sender instanceof Player) ? (Player) sender : null;

        // Xử lý các lệnh cho người chơi
        switch (sub) {
            case "shop":
                if (p != null) openShop(p);
                return true;

            case "points":
                if (p != null) {
                    double money = getMoney(p.getUniqueId());
                    p.sendMessage("§aYour FukkuCoin balance: " + money);
                }
                return true;

            case "daily":
                if (p != null) {
                    if (canClaimDaily(p.getUniqueId())) {
                        int reward = getConfig().getInt("daily-reward", 100);
                        addMoney(p.getUniqueId(), reward);
                        setDailyClaimed(p.getUniqueId());
                        p.sendMessage("§aYou received your daily reward: " + reward + " FukkuCoin!");
                    } else {
                        p.sendMessage("§cYou can only claim your daily reward once every 24 hours.");
                    }
                }
                return true;

            case "pay":
                if (p != null) {
                    if (args.length < 3) {
                        p.sendMessage("Usage: /fukkucoin pay <player> <amount>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        p.sendMessage("§cPlayer not found or offline.");
                        return true;
                    }
                    if (target.getUniqueId().equals(p.getUniqueId())) {
                        p.sendMessage("§cYou cannot pay yourself.");
                        return true;
                    }
                    double amountPay;
                    try {
                        amountPay = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        p.sendMessage("§cInvalid amount.");
                        return true;
                    }
                    if (amountPay <= 0) {
                        p.sendMessage("§cAmount must be positive.");
                        return true;
                    }
                    if (removeMoney(p.getUniqueId(), amountPay)) {
                        addMoney(target.getUniqueId(), amountPay);
                        p.sendMessage("§aYou paid " + amountPay + " FukkuCoin to " + target.getName());
                        target.sendMessage("§aYou received " + amountPay + " FukkuCoin from " + p.getName());
                    } else {
                        p.sendMessage("§cYou don't have enough FukkuCoin.");
                    }
                }
                return true;

            case "buy":
                if (p != null) {
                    if (args.length < 2) {
                        p.sendMessage("Usage: /fukkucoin buy <item>");
                        return true;
                    }
                    String itemName = args[1].toLowerCase();
                    if (!shopItems.containsKey(itemName)) {
                        p.sendMessage("§cItem not found in shop.");
                        return true;
                    }
                    double price = shopItems.get(itemName);
                    if (getMoney(p.getUniqueId()) >= price) {
                        removeMoney(p.getUniqueId(), price);
                        giveItemToPlayer(p, itemName);
                        p.sendMessage("§aYou bought " + itemName + " for " + price + " FukkuCoin.");
                    } else {
                        p.sendMessage("§cYou don't have enough FukkuCoin to buy " + itemName + ".");
                    }
                }
                return true;
        }

        // Các lệnh admin
        if (!sender.hasPermission("fukkucoin.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        switch (sub) {
            case "addmoney":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /fukkucoin addmoney <player> <amount>");
                    return true;
                }
                Player targetAdd = Bukkit.getPlayer(args[1]);
                if (targetAdd == null) {
                    sender.sendMessage("Player not found or offline.");
                    return true;
                }
                double amountAdd;
                try {
                    amountAdd = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid amount.");
                    return true;
                }
                addMoney(targetAdd.getUniqueId(), amountAdd);
                sender.sendMessage("Added " + amountAdd + " FukkuCoin to " + targetAdd.getName());
                targetAdd.sendMessage("You received " + amountAdd + " FukkuCoin.");
                return true;

            case "removemoney":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /fukkucoin removemoney <player> <amount>");
                    return true;
                }
                Player targetRemove = Bukkit.getPlayer(args[1]);
                if (targetRemove == null) {
                    sender.sendMessage("Player not found or offline.");
                    return true;
                }
                double amountRemove;
                try {
                    amountRemove = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid amount.");
                    return true;
                }
                if (removeMoney(targetRemove.getUniqueId(), amountRemove)) {
                    sender.sendMessage("Removed " + amountRemove + " FukkuCoin from " + targetRemove.getName());
                    targetRemove.sendMessage(amountRemove + " FukkuCoin was taken from your balance.");
                } else {
                    sender.sendMessage("Player does not have enough FukkuCoin.");
                }
                return true;

            case "reload":
                reloadConfig();
                sender.sendMessage("FukkuCoin plugin config reloaded.");
                return true;

            case "npc":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /fukkucoin npc <create|move> [...]");
                    return true;
                }
                String subnpc = args[1].toLowerCase();
                if (subnpc.equals("create")) {
                    if (args.length < 3) {
                        sender.sendMessage("Usage: /fukkucoin npc create <name>");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("This command must be run by a player.");
                        return true;
                    }
                    Player pl = (Player) sender;
                    String npcName = args[2];
                    createNPC(pl, npcName);
                    sender.sendMessage("NPC " + npcName + " created at your location.");
                    return true;
                } else if (subnpc.equals("move")) {
                    if (args.length < 6) {
                        sender.sendMessage("Usage: /fukkucoin npc move <npcUUID> <x> <y> <z>");
                        return true;
                    }
                    String npcUUID = args[2];
                    try {
                        double x = Double.parseDouble(args[3]);
                        double y = Double.parseDouble(args[4]);
                        double z = Double.parseDouble(args[5]);
                        moveNPC(npcUUID, x, y, z);
                        sender.sendMessage("NPC " + npcUUID + " moved.");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid coordinates.");
                    }
                    return true;
                }
                sender.sendMessage("Invalid npc command.");
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    // --- Các hàm hỗ trợ ---

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6FukkuCoin commands:");
        sender.sendMessage("/fukkucoin shop - Show shop items");
        sender.sendMessage("/fukkucoin points - Check your points");
        sender.sendMessage("/fukkucoin daily - Claim daily reward");
        sender.sendMessage("/fukkucoin pay <player> <amount> - Pay money to player");
        sender.sendMessage("/fukkucoin buy <item> - Buy an item from shop");
        sender.sendMessage("/fukkucoin addmoney <player> <amount> - Admin add money");
        sender.sendMessage("/fukkucoin removemoney <player> <amount> - Admin remove money");
        sender.sendMessage("/fukkucoin reload - Reload plugin config");
        sender.sendMessage("/fukkucoin npc create <name> - Create NPC");
        sender.sendMessage("/fukkucoin npc move <npcUUID> <x> <y> <z> - Move NPC");
    }

    // Lấy tiền của player
    private double getMoney(UUID uuid) {
        return moneyConfig.getDouble("money." + uuid.toString(), 0.0);
    }

    // Thêm tiền cho player và lưu file
    private void addMoney(UUID uuid, double amount) {
        double current = getMoney(uuid);
        moneyConfig.set("money." + uuid.toString(), current + amount);
        saveMoneyConfig();
    }

    // Trừ tiền player, trả true nếu đủ tiền
    private boolean removeMoney(UUID uuid, double amount) {
        double current = getMoney(uuid);
        if (current >= amount) {
            moneyConfig.set("money." + uuid.toString(), current - amount);
            saveMoneyConfig();
            return true;
        }
        return false;
    }

    private void saveMoneyConfig() {
        try {
            moneyConfig.save(moneyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Kiểm tra có thể nhận daily không (24h)
    private boolean canClaimDaily(UUID uuid) {
        if (!dailyClaimTime.containsKey(uuid)) return true;
        long lastClaim = dailyClaimTime.get(uuid);
        long now = System.currentTimeMillis();
        return now - lastClaim >= 24L * 60 * 60 * 1000;
    }

    // Cập nhật thời gian nhận daily hiện tại
    private void setDailyClaimed(UUID uuid) {
        dailyClaimTime.put(uuid, System.currentTimeMillis());
        saveMoneyConfig();
    }

    // Mở shop, liệt kê item
    private void openShop(Player player) {
        player.sendMessage("§6==== FukkuCoin Shop ====");
        for (Map.Entry<String, Double> entry : shopItems.entrySet()) {
            player.sendMessage("§e" + entry.getKey() + " - Price: " + entry.getValue());
        }
        player.sendMessage("Use /fukkucoin buy <item> to purchase.");
    }

    // Cho vật phẩm theo tên item
    private void giveItemToPlayer(Player player, String itemName) {
        ItemStack item = null;
        switch (itemName) {
            case "sword":
                item = new ItemStack(Material.DIAMOND_SWORD);
                break;
            case "shield":
                item = new ItemStack(Material.SHIELD);
                break;
            case "apple":
                item = new ItemStack(Material.APPLE, 5);
                break;
        }
        if (item != null) {
            player.getInventory().addItem(item);
        } else {
            player.sendMessage("§cItem " + itemName + " không được hỗ trợ.");
        }
    }

    // --- NPC quản lý đơn giản ---

    private void createNPC(Player player, String name) {
        UUID npcUUID = UUID.randomUUID();
        Location loc = player.getLocation();
        npcConfig.set("npcs." + npcUUID.toString() + ".name", name);
        npcConfig.set("npcs." + npcUUID.toString() + ".world", loc.getWorld().getName());
        npcConfig.set("npcs." + npcUUID.toString() + ".x", loc.getX());
        npcConfig.set("npcs." + npcUUID.toString() + ".y", loc.getY());
        npcConfig.set("npcs." + npcUUID.toString() + ".z", loc.getZ());
        saveNpcConfig();
    }

    private void moveNPC(String npcUUID, double x, double y, double z) {
        String path = "npcs." + npcUUID;
        if (!npcConfig.contains(path)) return;
        String worldName = npcConfig.getString(path + ".world");
        if (worldName == null) return;

        npcConfig.set(path + ".x", x);
        npcConfig.set(path + ".y", y);
        npcConfig.set(path + ".z", z);
        saveNpcConfig();
    }

    private void saveNpcConfig() {
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
