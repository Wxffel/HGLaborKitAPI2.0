package de.hglabor.plugins.kitapi.kit.kits;

import de.hglabor.plugins.kitapi.kit.AbstractKit;
import de.hglabor.plugins.kitapi.kit.KitManager;
import de.hglabor.plugins.kitapi.kit.config.KitSettings;
import de.hglabor.plugins.kitapi.player.KitPlayer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

public class SmogmogKit extends AbstractKit implements Listener {
    public final static SmogmogKit INSTANCE = new SmogmogKit();

    private SmogmogKit() {
        super("Smogmog", Material.POPPED_CHORUS_FRUIT, 20);
        addSetting(KitSettings.EFFECT_DURATION, 3);
        setMainKitItem(getDisplayMaterial());
    }

    @Override
    public void onPlayerRightClickKitItem(PlayerInteractEvent e) {
        e.setCancelled(true);
        AreaEffectCloud cloud = (AreaEffectCloud) e.getPlayer().getWorld().spawnEntity(e.getPlayer().getLocation(), EntityType.AREA_EFFECT_CLOUD);
        cloud.setCustomName(e.getPlayer().getUniqueId().toString());
        cloud.setColor(Color.fromBGR(201, 110, 235));
        cloud.setDuration((Integer) getSetting(KitSettings.EFFECT_DURATION) * 20);
        cloud.setSource(e.getPlayer());
        cloud.setBasePotionData(new PotionData(PotionType.INSTANT_DAMAGE, false, false));
        KitPlayer kitPlayer = KitManager.getInstance().getPlayer(e.getPlayer());
        kitPlayer.activateKitCooldown(this, this.getCooldown());
    }

    @EventHandler
    public void onAreaEffectCloudDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof AreaEffectCloud) {
            Player involved = (Player) e.getEntity();
            AreaEffectCloud cloud = (AreaEffectCloud) e.getDamager();
            if (involved.getUniqueId().toString().equals(cloud.getCustomName())) {
                e.setCancelled(true);
            }
        }
    }
}