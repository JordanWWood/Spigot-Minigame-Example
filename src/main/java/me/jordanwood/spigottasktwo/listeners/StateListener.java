package me.jordanwood.spigottasktwo.listeners;

import me.jordanwood.spigottasktwo.SpigotTaskTwo;
import me.jordanwood.spigottasktwo.config.WorldConfig;
import me.jordanwood.spigottasktwo.events.StateChangeEvent;
import me.jordanwood.spigottasktwo.managers.StateManager;
import me.jordanwood.spigottasktwo.scoreboard.Scoreboard;
import me.jordanwood.spigottasktwo.scoreboard.row.SpacerRow;
import me.jordanwood.spigottasktwo.scoreboard.row.TextRow;
import me.jordanwood.spigottasktwo.utils.State;
import me.jordanwood.spigottasktwo.utils.Title;

import net.md_5.bungee.api.ChatColor;

import net.minecraft.server.v1_12_R1.AttributeInstance;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.GenericAttributes;

import org.bukkit.*;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.*;

public class StateListener implements Listener {
    private static final String scoreboardTitle = ChatColor.GOLD + "" + ChatColor.BOLD + "SpigotTask2";

    static WorldConfig worldConfig;
    private Map<UUID, Location> previousLocations;

    @EventHandler
    public void onStateChange(StateChangeEvent e) {
        switch (e.getState()) {
            case WAITING:
                onWaiting();
                break;
            case PRESTART:
                onPreStart();
                break;
            case INGAME:
                onInGame();
                break;
            case ENDGAME:
                onEndGame();
                break;
        }
    }

    private void onWaiting() {
        SpigotTaskTwo.getStateManager().registerListeners(new WaitingListener());

        SpigotTaskTwo.getInstance().getLogger().info("Running1");
        WaitingListener.waiting = new Scoreboard(scoreboardTitle);
        WaitingListener.countDown = new Scoreboard(scoreboardTitle);

        TextRow timerTitle = new TextRow(ChatColor.GOLD + "" + ChatColor.BOLD + "Starting in:");
        WaitingListener.countDown.addRows(new SpacerRow(), timerTitle);
        TextRow waiting = new TextRow("Awaiting players...");
        WaitingListener.waiting.addRows(new SpacerRow(), timerTitle, waiting, new SpacerRow());

        TextRow timer = new TextRow("00:10");
        WaitingListener.countDownTimeRow = WaitingListener.countDown.addRow(timer);
        WaitingListener.countDown.addRow(new SpacerRow());

        TextRow playersTitle = new TextRow(ChatColor.GOLD + "" + ChatColor.BOLD + "Players:");
        WaitingListener.waiting.addRow(playersTitle);
        WaitingListener.countDown.addRow(playersTitle);

        TextRow players = new TextRow("1/12");
        WaitingListener.playerCountWaitingRow = WaitingListener.waiting.addRow(players);
        WaitingListener.playerCountCountDownRow = WaitingListener.countDown.addRow(players);
        SpigotTaskTwo.getScoreboardManager().setScoreboard(WaitingListener.waiting);
    }

    private void onPreStart() {
        SpigotTaskTwo.getStateManager().registerListeners(new PreStartListener());

        WorldCreator wc = new WorldCreator("racetrack");
        wc.generateStructures(false);
        wc.environment(org.bukkit.World.Environment.NORMAL);
        wc.seed(0);
        wc.generator(new BlankChunkGenerator());
        World world = Bukkit.createWorld(wc);

        InGameListener.gameBoard = new Scoreboard(scoreboardTitle);
        InGameListener.gameBoard.addRows(new SpacerRow(), new TextRow(ChatColor.GOLD + "" + ChatColor.BOLD + "Players:"));

        int index = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Vector v = worldConfig.getSpawnLocations().get(index);
            previousLocations.put(p.getUniqueId(), p.getLocation());

            p.teleport(new Location(world, v.getX(), v.getY(), v.getZ()));

            InGameListener.gameBoard.addRow(new TextRow(p.getName()));
            spawnHorse(p);
        }

        tickTask(10, true, 1, () -> {
            tickTask(300, false, 60,() -> {
                if (SpigotTaskTwo.getStateManager().getCurrentState() == State.INGAME) {
                    SpigotTaskTwo.getStateManager().nextState();

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(ChatColor.RED + "Time has expired!");
                    }
                }
            });
        });

        SpigotTaskTwo.getScoreboardManager().setScoreboard(InGameListener.gameBoard);
    }

    private void onInGame() {
        SpigotTaskTwo.getStateManager().registerListeners(new InGameListener());

        Bukkit.getOnlinePlayers().forEach(p -> InGameListener.hasPassedThroughPortal.put(p.getUniqueId(), false));
    }

    private void onEndGame() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendMessage(ChatColor.GOLD + EndListener.getWinner().getName() + " has won!");
            player.sendMessage(ChatColor.GOLD + "You will be moved back to lobby in 10 seconds");
        });

        tickTask(10, false, 1, () -> {
            for (Player player : Bukkit.getOnlinePlayers())
                player.teleport(previousLocations.get(player.getUniqueId()));
        });
    }

    private void tickTask(int timer, boolean title, int intervaul, @Nullable Runnable onEnd) {
        Bukkit.getScheduler().runTaskLater(SpigotTaskTwo.getInstance(), () -> {

            if (timer % intervaul == 0) {
                Bukkit.getOnlinePlayers().forEach((player) -> {
                    if (title) {
                        Title.createTitle(player, String.valueOf(timer), "");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "" + timer);
                    }
                });
            }

            if (timer != 0) {
                tickTask(timer - 1, title, intervaul, onEnd);
                return;
            }

            Bukkit.getScheduler().runTask(SpigotTaskTwo.getInstance(), () -> SpigotTaskTwo.getStateManager().nextState());

            if (onEnd != null) {
                Bukkit.getScheduler().runTask(SpigotTaskTwo.getInstance(), onEnd);
            }
        }, 20L);
    }

    private static void spawnHorse(Player p) {
        Horse h = p.getWorld().spawn(p.getLocation(), Horse.class);

        AttributeInstance speed = ((EntityLiving) ((CraftEntity) h).getHandle())
                .getAttributeInstance(GenericAttributes.MOVEMENT_SPEED);
        speed.setValue(0.5D);

        h.setTamed(true);
        h.setOwner(p);
        h.setJumpStrength(1.0D);
        h.setHealth(50.0D);
        h.setAdult();
        h.getInventory().setSaddle(new ItemStack(Material.SADDLE));

        h.setPassenger(p);
    }
}

class BlankChunkGenerator extends ChunkGenerator {
    @Override
    public byte[][] generateBlockSections(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
        return new byte[world.getMaxHeight() / 16][];
    }
}

