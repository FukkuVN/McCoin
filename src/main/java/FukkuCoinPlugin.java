package fukkucoin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class FukkuCoinPlugin extends JavaPlugin implements Listener {

    private File coinsFile;
    private FileConfiguration coinsConfig;

    private File dataFile;
    private FileConfiguration dataConfig;

    private HashMap<UUID, Integer> coins = new HashMap<>();
    private HashMap<UUID, Long> lastClaim = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCoins();
        loadData();

        getServer().getPluginManager().registerEvents(this, this);

        // Tạo NPC theo config
        createNPCs();

        // Lên lịch lưu coins và data mỗi 5 phút
        new BukkitRunnable() {
            @Override
            public void run() {
                saveCoins();
                saveData();
            }
        }.runTaskTimer(this, 6000, 6000);

        getLogger().info("FukkuCoinPlugin enabled!");
    }

    @Override
    public void onDisable() {
        saveCoins();
        saveData();
        getLogger().info("FukkuCoinPlugin disabled!");
    }

    private void createNPCs() {
        if (!CitizensAPI.hasImplementation()) {
            getLogger().warning("Citizens plugin not found! NPC creation skipped.");
            return;
        }

        getLogger().info("Creating NPCs from config...");

        for (String key : getConfig().getConfigurationSection("npcs").getKeys(false)) {
            String name = getConfig().getString("npcs." + key + ".name");
            String world = getConfig().getString("npcs." + key + ".world");
            double x = getConfig().getDouble("npcs." + key + ".x");
            double y = getConfig().getDouble("npcs." + key + ".y");
            double z = getConfig().getDouble("npcs." + key + ".z");
            float yaw = (float) getConfig().getDouble("npcs." + key + ".yaw");
            float pitch = (float) getConfig().getDouble("npcs." + key + ".pitch");

            Location loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);

            NPC npc = CitizensAPI.getNPCRegistry().createNPC(net.citizensnpcs.api.npc.NPCType.PLAYER, ChatColor.GREEN + name);
            npc.spawn(loc);
            getLogger().info("Spawned NPC " + name);
        }
    }

    @EventHandler
    public void onPlayerInteractNPC(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof org.bukkit.entity.Player)) return;

        NPC npc = CitizensAPI.getNPCRegistry().getNPC(e.getRightClicked());
        if (npc == null) return;

        String npcName = ChatColor.stripColor(npc.getName());

        Player p = e.getPlayer();

        if (npcName.equalsIgnoreCase("FukkuShop")) {
            e.setCancelled(true);
            openShop(p);
        }
    }

    private void openShop(Player p) {
        p.sendMessage(ChatColor.GOLD + "=== FukkuCoin Shop ===");
        p.sendMessage(ChatColor.YELLOW + "Bạn có " + ChatColor.GREEN + getCoins(p.getUniqueId()) + " FukkuCoin.");
        p.sendMessage(ChatColor.YELLOW + "Dùng lệnh /fukkubuy <item> để mua.");
    }

    // Simple coin system
    public int getCoins(UUID uuid) {
        return coins.getOrDefault(uuid, 0);
    }

    public void addCoins(UUID uuid, int amount) {
        coins.put(uuid, getCoins(uuid) + amount);
    }

    public boolean takeCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        if (current >= amount) {
            coins.put(uuid, current - amount);
            return true;
        }
        return false;
    }

    private void loadCoins() {
        coinsFile = new File(getDataFolder(), "coins.yml");
        if (!coinsFile.exists()) {
            saveResource("coins.yml", false);
        }
        coinsConfig = YamlConfiguration.loadConfiguration(coinsFile);
        for (String key : coinsConfig.getKeys(false)) {
            coins.put(UUID.fromString(key), coinsConfig.getInt(key));
        }
    }

    private void saveCoins() {
        for (UUID uuid : coins.keySet()) {
            coinsConfig.set(uuid.toString(), coins.get(uuid));
        }
        try {
            coinsConfig.save(coinsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            lastClaim.put(UUID.fromString(key), dataConfig.getLong(key));
        }
    }

    private void saveData() {
        for (UUID uuid : lastClaim.keySet()) {
            dataConfig.set(uuid.toString(), lastClaim.get(uuid));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Điểm danh nhận 50 coin mỗi 24h
    public boolean claimDaily(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastClaim.getOrDefault(uuid, 0L);
        if (now - last >= 86400000L) { // 24h = 86400000 ms
            addCoins(uuid, 50);
            lastClaim.put(uuid, now);
            p.sendMessage(ChatColor.GREEN + "Bạn đã nhận 50 FukkuCoin điểm danh hàng ngày!");
            return true;
        } else {
            p.sendMessage(ChatColor.RED + "Bạn đã điểm danh hôm nay rồi! Hãy quay lại sau.");
            return false;
        }
    }
}
