package de.hglabor.plugins.kitapi.kit.kits;

import de.hglabor.plugins.kitapi.kit.AbstractKit;
import de.hglabor.plugins.kitapi.KitApi;
import de.hglabor.plugins.kitapi.kit.config.KitSettings;
import de.hglabor.plugins.kitapi.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Collections;

/**
 * Hommage an Waffel :) (wtf?)
 */
public class JackhammerKit extends AbstractKit {
    public final static JackhammerKit INSTANCE = new JackhammerKit();

    private JackhammerKit() {
        super("Jackhammer", Material.STONE_AXE, 20);
        setMainKitItem(getDisplayMaterial(), true);
        addSetting(KitSettings.USES, 5);
        addEvents(Collections.singletonList(BlockBreakEvent.class));
    }

    @Override
    public void onBlockBreakWithKitItem(BlockBreakEvent e) {
        Location blockLoc = e.getBlock().getLocation();
        Block above = blockLoc.clone().add(0, 1,0).getBlock();
        Block below = blockLoc.clone().subtract(0, 1,0).getBlock();

        if (above.getType().isAir() || above.getType().getHardness() == 100.0f) {
            // DOWN
            dig(block.getLocation(), -1, 1);
        } else if (below.getType().isAir() || below.getType().getHardness() == 100.0f) {
            // UP
            dig(block.getLocation(), 1, 1);
        } else {
            // UP & DOWN but with half dig speed
            dig(block.getLocation(), 1, 2);
            dig(block.getLocation(), -1, 2);
        }
        KitApi.getInstance().checkUsesForCooldown(e.getPlayer(), this);
    }

    /**
     * @param loc       Location to start
     * @param direction -1 = down; 1 = up; 0 = both
     */
    private void dig(Location loc, int direction, int delay) {
        final Location currentLocation = loc.clone();

        Bukkit.getScheduler().runTaskTimer(KitApi.getInstance().getPlugin(), bukkitTask -> {
            if (!Utils.isUnbreakableLaborBlock(currentLocation.getBlock())) {
                currentLocation.getBlock().setType(Material.AIR);
                loc.getWorld().spawnParticle(Particle.ASH, currentLocation.clone().add(.5, 0, .5), 10);
                currentLocation.add(0, direction, 0);
                if (currentLocation.getBlock().getType() == Material.BEDROCK) {
                    bukkitTask.cancel();
                }
            } else {
                bukkitTask.cancel();
            }
        }, 0, delay);
    }
}
