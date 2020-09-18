package me.petterim1.updatenotificationservice;

import cn.nukkit.Nukkit;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.ConsoleCommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerLocallyInitializedEvent;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.PluginBase;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class Main extends PluginBase implements Listener {

    private static final Properties GIT_INFO = getGitInfo();
    private String currentVer;
    private String currentBranch;
    private boolean opJoin;
    private boolean opVersion;
    private boolean skipDuplicate;
    private String alreadyNotified;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getServer().getName().equals("Nukkit")) {
            getLogger().error("§cThis plugin supports only official Nukkit build!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        currentVer = getVersion();
        currentBranch = getBranch();
        if (currentVer.equals("null") || currentBranch.equals("null")) {
            getLogger().error("§cMissing or invalid git info! This plugin supports only official Nukkit builds!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else if (!currentBranch.equals("master")) {
            getLogger().info("§eNon master branch build detected! Trying to check updates for branch '" + currentBranch + '\'');
        }
        opJoin = getConfig().getBoolean("update-notifications.on-op-join-server");
        opVersion = getConfig().getBoolean("update-notifications.on-op-version-command");
        skipDuplicate = getConfig().getBoolean("skip-duplicated-notifications");
        if (getConfig().getInt("update-check.minutes") > 0) {
            getServer().getScheduler().scheduleRepeatingTask(this, () -> checkForUpdates(getServer().getConsoleSender(), true, false), getConfig().getInt("update-check.minutes") * 1200);
        } else if (getConfig().getBoolean("update-notifications.on-server-startup")) {
            checkForUpdates(getServer().getConsoleSender(), false, false);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private static Properties getGitInfo() {
        InputStream gitFileStream = Nukkit.class.getClassLoader().getResourceAsStream("git.properties");
        if (gitFileStream == null) {
            return null;
        }
        Properties properties = new Properties();
        try {
            properties.load(gitFileStream);
        } catch (IOException e) {
            return null;
        }
        return properties;
    }

    private String getVersion() {
        StringBuilder version = new StringBuilder();
        String commitId;
        if (GIT_INFO == null || (commitId = GIT_INFO.getProperty("git.commit.id.abbrev")) == null) {
            return version.append("null").toString();
        }
        return version.append(commitId).toString();
    }

    private String getBranch() {
        String branch;
        if (GIT_INFO == null || (branch = GIT_INFO.getProperty("git.branch")) == null) {
            return "null";
        }
        return branch;
    }

    private void checkForUpdates(CommandSender sender, boolean scheduled, boolean command) {
        CompletableFuture.runAsync(() -> {
            try {
                URLConnection request = new URL("https://api.github.com/repos/CloudburstMC/Nukkit/commits/" + currentBranch).openConnection();
                request.connect();
                InputStreamReader content = new InputStreamReader((InputStream) request.getContent());
                String latest = new JsonParser().parse(content).getAsJsonObject().get("sha").getAsString().substring(0, 7);
                content.close();
                if (!scheduled || !skipDuplicate || alreadyNotified == null || !alreadyNotified.equals(latest) || !(sender instanceof ConsoleCommandSender)) {
                    if (!currentVer.equals(latest)) {
                        sender.sendMessage("§aThere is an update available for §cNukkit§a! §7(Current version: §e" + currentBranch + '/' + currentVer + "§7, Latest version: §e" + currentBranch + '/' + latest + "§7)");
                        alreadyNotified = latest;
                    } else if (command) {
                        sender.sendMessage("§aYou are running the latest version!");
                    }
                }
                this.getLogger().debug("§aUpdate check done");
            } catch (Exception ex) {
                this.getLogger().debug("§cUpdate check failed", ex);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerLocallyInitializedEvent e) {
        if (opJoin && e.getPlayer().isOp()) {
            checkForUpdates(e.getPlayer(), false, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("version") || command.getName().equalsIgnoreCase("ver")) {
            sender.sendMessage("§eThis server is running Nukkit version " + currentBranch + "/" + currentVer + " implementing API version " + getServer().getApiVersion() + " for Minecraft Bedrock Edition " + getServer().getVersion() + " (Protocol version " + ProtocolInfo.CURRENT_PROTOCOL + ')');
            if (opVersion && sender.isOp()) {
                checkForUpdates(sender, false, true);
            }
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }
}
