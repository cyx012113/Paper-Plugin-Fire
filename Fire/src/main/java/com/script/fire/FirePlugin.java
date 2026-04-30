package com.script.fire;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class FirePlugin extends JavaPlugin implements Listener {

    // Economy & balance reset
    private Economy economy;
    private List<Pattern> economyResetWorlds = new ArrayList<>();
    private String economyBypassPermission = "fire.bypass.clear";

    // Block protection
    private final Map<World, Set<Location>> placedBlocks = new ConcurrentHashMap<>();
    private List<Pattern> blockProtectionWorlds = new ArrayList<>();
    private String blockBypassPermission = "fire.bypass.block";

    // Build restriction
    private List<Pattern> noBuildWorlds = new ArrayList<>();
    private String buildBypassPermission = "fire.bypass.build";

    // World rules
    private List<Pattern> pvpDisabledWorlds = new ArrayList<>();
    private List<Pattern> invincibleWorlds = new ArrayList<>();
    private List<Pattern> saturateWorlds = new ArrayList<>();
    private List<Pattern> voidRespawnWorlds = new ArrayList<>();

    // Anti-spam
    private int spamIntervalSeconds = 3;
    private int spamMaxMessages = 2;
    private final Map<UUID, Deque<Long>> playerMessageTimes = new HashMap<>();

    // Broadcast system
    private File broadcastDataFile;
    private YamlConfiguration broadcastData;
    private int defaultBroadcastCredits = 3;

    // Invite system
    private int inviteCooldownSeconds = 10;
    private final Map<UUID, Long> lastInviteTime = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    private static class PendingInvite {
        String inviterName;
        String targetWorldName;
        long expiry;
        PendingInvite(String inviter, String world, long expiry) {
            this.inviterName = inviter;
            this.targetWorldName = world;
            this.expiry = expiry;
        }
    }

    private long economyResetInterval = 1200; // ticks

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found – disabling economy features (balance reset)");
            economy = null;
        }

        broadcastDataFile = new File(getDataFolder(), "broadcast_credits.yml");
        if (!broadcastDataFile.exists()) {
            try {
                broadcastDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        broadcastData = YamlConfiguration.loadConfiguration(broadcastDataFile);
        if (!broadcastData.contains("default-credits")) {
            broadcastData.set("default-credits", defaultBroadcastCredits);
            saveBroadcastData();
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        startEconomyResetTask();

        getLogger().info("FirePlugin v2.0.0 enabled.");
    }

    @Override
    public void onDisable() {
        saveBroadcastData();
        placedBlocks.clear();
        playerMessageTimes.clear();
        lastInviteTime.clear();
        pendingInvites.clear();
        getLogger().info("FirePlugin disabled.");
    }

    private void reloadPluginConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        economyResetWorlds = compilePatterns(config.getStringList("economy-reset-worlds"));
        economyResetInterval = config.getLong("economy-reset-interval-ticks", 1200);

        blockProtectionWorlds = compilePatterns(config.getStringList("block-protection-worlds"));
        noBuildWorlds = compilePatterns(config.getStringList("no-build-worlds"));

        pvpDisabledWorlds = compilePatterns(config.getStringList("pvp-disabled-worlds"));
        invincibleWorlds = compilePatterns(config.getStringList("invincible-worlds"));
        saturateWorlds = compilePatterns(config.getStringList("saturate-worlds"));
        voidRespawnWorlds = compilePatterns(config.getStringList("void-respawn-worlds"));

        spamIntervalSeconds = config.getInt("anti-spam.interval-seconds", 3);
        spamMaxMessages = config.getInt("anti-spam.max-messages", 2);

        defaultBroadcastCredits = config.getInt("broadcast.default-credits", 3);
        if (broadcastData != null && !broadcastData.contains("default-credits")) {
            broadcastData.set("default-credits", defaultBroadcastCredits);
            saveBroadcastData();
        }

        inviteCooldownSeconds = config.getInt("invite.cooldown-seconds", 10);
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        return patterns.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(p -> {
                    String regex = p.replace("*", ".*");
                    try {
                        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    } catch (PatternSyntaxException e) {
                        getLogger().warning("Invalid pattern: " + p);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean matchesAny(World world, List<Pattern> patterns) {
        if (world == null) return false;
        String name = world.getName();
        for (Pattern p : patterns) {
            if (p.matcher(name).matches()) return true;
        }
        return false;
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void startEconomyResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (!matchesAny(world, economyResetWorlds)) continue;
                    for (Player p : world.getPlayers()) {
                        if (p.hasPermission(economyBypassPermission)) continue;
                        if (p.isOp() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)
                            continue;
                        if (economy != null) {
                            double balance = economy.getBalance(p);
                            if (balance > 0) {
                                economy.withdrawPlayer(p, balance);
                                p.setLevel(0);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, economyResetInterval);
    }

    private boolean isBlockProtected(World world) {
        return matchesAny(world, blockProtectionWorlds);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        World w = p.getWorld();
        if (matchesAny(w, noBuildWorlds) && !p.hasPermission(buildBypassPermission)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You cannot place blocks in this world.");
            return;
        }
        if (isBlockProtected(w)) {
            placedBlocks.computeIfAbsent(w, k -> ConcurrentHashMap.newKeySet()).add(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        World w = p.getWorld();
        if (p.hasPermission(blockBypassPermission)) {
            Set<Location> placed = placedBlocks.get(w);
            if (placed != null) placed.remove(event.getBlock().getLocation());
            return;
        }
        if (!isBlockProtected(w)) return;
        Set<Location> placed = placedBlocks.get(w);
        if (placed == null || !placed.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You cannot break natural terrain!");
        } else {
            placed.remove(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        World w = block.getWorld();
        if (!isBlockProtected(w)) return;
        Set<Location> placed = placedBlocks.get(w);
        if (placed == null || !placed.contains(block.getLocation())) {
            event.setCancelled(true);
        } else {
            placed.remove(block.getLocation());
        }
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        filterExplodedBlocks(event.blockList(), event.getBlock().getWorld());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplodedBlocks(event.blockList(), event.getLocation().getWorld());
    }

    private void filterExplodedBlocks(List<Block> blocks, World world) {
        if (!isBlockProtected(world)) return;
        Set<Location> placed = placedBlocks.get(world);
        if (placed == null) {
            blocks.clear();
            return;
        }
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Location loc = b.getLocation();
            if (!placed.contains(loc)) {
                it.remove();
            } else {
                placed.remove(loc);
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        World w = attacker.getWorld();
        if (matchesAny(w, pvpDisabledWorlds)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        World w = p.getWorld();

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && matchesAny(w, voidRespawnWorlds)) {
            event.setCancelled(true);
            Location spawn = w.getSpawnLocation();
            p.teleport(spawn);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.sendMessage(ChatColor.GREEN + "You have been rescued from the void.");
            return;
        }

        if (matchesAny(w, invincibleWorlds)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        World w = p.getWorld();
        if (matchesAny(w, saturateWorlds)) {
            event.setCancelled(true);
            if (p.getFoodLevel() < 20) {
                p.setFoodLevel(20);
                p.setSaturation(20f);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> times = playerMessageTimes.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() < now - spamIntervalSeconds * 1000L) {
            times.pollFirst();
        }
        if (times.size() >= spamMaxMessages) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You are sending messages too quickly. Please wait.");
            return;
        }
        times.addLast(now);
    }

    private int getBroadcastCredits(Player p) {
        String uuid = p.getUniqueId().toString();
        if (!broadcastData.contains("players." + uuid)) {
            broadcastData.set("players." + uuid, defaultBroadcastCredits);
            saveBroadcastData();
        }
        return broadcastData.getInt("players." + uuid);
    }

    private void setBroadcastCredits(Player p, int amount) {
        if (amount < 0) amount = 0;
        broadcastData.set("players." + p.getUniqueId().toString(), amount);
        saveBroadcastData();
    }

    private void addBroadcastCredits(Player p, int amount) {
        int current = getBroadcastCredits(p);
        setBroadcastCredits(p, current + amount);
    }

    private void saveBroadcastData() {
        try {
            broadcastData.save(broadcastDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save broadcast_credits.yml: " + e.getMessage());
        }
    }

    public boolean performBroadcast(Player sender, String message) {
        int credits = getBroadcastCredits(sender);
        if (credits <= 0) {
            sender.sendMessage(ChatColor.RED + "You have no broadcast credits left. Ask an admin to add more.");
            return false;
        }
        setBroadcastCredits(sender, credits - 1);
        String formatted = ChatColor.translateAlternateColorCodes('&',
                "&6[Broadcast] &f" + sender.getName() + ": &e" + message);
        Bukkit.broadcastMessage(formatted);
        return true;
    }

    private boolean canSendInvite(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastInviteTime.get(p.getUniqueId());
        if (last != null && now - last < inviteCooldownSeconds * 1000L) {
            p.sendMessage(ChatColor.RED + "You must wait " + inviteCooldownSeconds + " seconds before sending another invite.");
            return false;
        }
        return true;
    }

    private void sendInviteMessage(Player inviter, Player target, String worldName) {
        String clickCommand = "/invite accept " + inviter.getName();
        TextComponent message = new TextComponent(
                ChatColor.GREEN + inviter.getName() + ChatColor.YELLOW + " has invited you to join world " +
                ChatColor.AQUA + worldName + ChatColor.YELLOW + ". Click here to teleport!"
        );
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
        target.spigot().sendMessage(message);
    }

    private void sendAllInvite(Player inviter, String worldName) {
        String clickCommand = "/invite accept " + inviter.getName();
        TextComponent message = new TextComponent(
                ChatColor.GREEN + inviter.getName() + ChatColor.YELLOW + " has invited everyone to join world " +
                ChatColor.AQUA + worldName + ChatColor.YELLOW + ". Click here to teleport!"
        );
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(inviter)) continue;
            online.spigot().sendMessage(message);
        }
        inviter.sendMessage(ChatColor.GREEN + "All players have been invited to your world.");
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        // Broadcast command
        if (cmd.getName().equalsIgnoreCase("broadcast") || cmd.getName().equalsIgnoreCase("bc")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("fire.broadcast.use")) {
                p.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "Usage: /broadcast <message>");
                return true;
            }
            String message = String.join(" ", args);
            performBroadcast(p, message);
            return true;
        }

        // Broadcast admin command
        if (cmd.getName().equalsIgnoreCase("broadcastadmin") || cmd.getName().equalsIgnoreCase("bcadmin")) {
            if (!sender.hasPermission("fire.broadcast.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /bcadmin <add|set|clear|check> <player> [amount]");
                return true;
            }
            String action = args[0].toLowerCase();
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not online.");
                return true;
            }
            switch (action) {
                case "add":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Specify amount.");
                        return true;
                    }
                    int addAmount = Integer.parseInt(args[2]);
                    addBroadcastCredits(target, addAmount);
                    sender.sendMessage(ChatColor.GREEN + "Added " + addAmount + " credits to " + target.getName());
                    break;
                case "set":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Specify amount.");
                        return true;
                    }
                    int setAmount = Integer.parseInt(args[2]);
                    setBroadcastCredits(target, setAmount);
                    sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s credits to " + setAmount);
                    break;
                case "clear":
                    setBroadcastCredits(target, 0);
                    sender.sendMessage(ChatColor.GREEN + "Cleared " + target.getName() + "'s credits.");
                    break;
                case "check":
                    int credits = getBroadcastCredits(target);
                    sender.sendMessage(ChatColor.YELLOW + target.getName() + " has " + credits + " broadcast credits.");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown action.");
            }
            return true;
        }

        // Invite command
        if (cmd.getName().equalsIgnoreCase("invite") || cmd.getName().equalsIgnoreCase("inv")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            Player inviter = (Player) sender;
            // Accept invite
            if (args.length >= 2 && args[0].equalsIgnoreCase("accept")) {
                if (!inviter.hasPermission("fire.invite.join")) {
                    inviter.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                String inviterName = args[1];
                PendingInvite invite = pendingInvites.get(inviter.getUniqueId());
                if (invite == null || !invite.inviterName.equalsIgnoreCase(inviterName) || invite.expiry < System.currentTimeMillis()) {
                    inviter.sendMessage(ChatColor.RED + "No valid invitation from that player.");
                    pendingInvites.remove(inviter.getUniqueId());
                    return true;
                }
                World targetWorld = Bukkit.getWorld(invite.targetWorldName);
                if (targetWorld == null) {
                    inviter.sendMessage(ChatColor.RED + "Target world no longer exists.");
                    pendingInvites.remove(inviter.getUniqueId());
                    return true;
                }
                inviter.teleport(targetWorld.getSpawnLocation());
                inviter.sendMessage(ChatColor.GREEN + "Teleported to " + invite.targetWorldName);
                pendingInvites.remove(inviter.getUniqueId());
                return true;
            }

            // Normal invite
            if (!inviter.hasPermission("fire.invite.send")) {
                inviter.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length == 0) {
                inviter.sendMessage(ChatColor.RED + "Usage: /invite <player|all> [world]");
                return true;
            }
            String targetName = args[0];
            World world = inviter.getWorld();
            if (args.length >= 2) {
                world = Bukkit.getWorld(args[1]);
                if (world == null) {
                    inviter.sendMessage(ChatColor.RED + "World not found.");
                    return true;
                }
            }
            String worldName = world.getName();

            if (targetName.equalsIgnoreCase("all")) {
                if (!canSendInvite(inviter)) return true;
                sendAllInvite(inviter, worldName);
                lastInviteTime.put(inviter.getUniqueId(), System.currentTimeMillis());
                return true;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                inviter.sendMessage(ChatColor.RED + "Player not online.");
                return true;
            }
            if (target.equals(inviter)) {
                inviter.sendMessage(ChatColor.RED + "You cannot invite yourself.");
                return true;
            }
            if (!canSendInvite(inviter)) return true;
            sendInviteMessage(inviter, target, worldName);
            pendingInvites.put(target.getUniqueId(), new PendingInvite(inviter.getName(), worldName, System.currentTimeMillis() + 30000));
            lastInviteTime.put(inviter.getUniqueId(), System.currentTimeMillis());
            inviter.sendMessage(ChatColor.GREEN + "Invitation sent to " + target.getName());
            return true;
        }

        // Admin reload command
        if (cmd.getName().equalsIgnoreCase("fireadmin") || cmd.getName().equalsIgnoreCase("fadmin")) {
            if (!sender.hasPermission("fire.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "FirePlugin configuration reloaded.");
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /fireadmin reload");
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingInvites.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        placedBlocks.remove(event.getWorld());
    }
}