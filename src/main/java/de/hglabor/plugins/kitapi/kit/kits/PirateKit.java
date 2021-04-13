package de.hglabor.plugins.kitapi.kit.kits;

import com.google.common.collect.ImmutableMap;
import de.hglabor.plugins.kitapi.KitApi;
import de.hglabor.plugins.kitapi.kit.MultipleKitItemsKit;
import de.hglabor.plugins.kitapi.kit.events.KitEvent;
import de.hglabor.plugins.kitapi.kit.items.KitItemAction;
import de.hglabor.plugins.kitapi.kit.items.KitItemBuilder;
import de.hglabor.plugins.kitapi.kit.settings.FloatArg;
import de.hglabor.plugins.kitapi.kit.settings.IntArg;
import de.hglabor.plugins.kitapi.player.KitPlayer;
import de.hglabor.plugins.kitapi.util.Logger;
import de.hglabor.utils.localization.Localization;
import de.hglabor.utils.noriskutils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.hglabor.utils.localization.Localization.t;

public class PirateKit extends MultipleKitItemsKit implements Listener {
    public static final PirateKit INSTANCE = new PirateKit();

    @IntArg
    private final int explosionBarrelsLimit, fireballSpeed;
    @FloatArg(min = 0.1F, max = 100f)
    private final float defaultExplosionPower, additionalExplosionPowerStep, maxAdditionalExplosionPower;

    private final String explosionBarrelMetaKey;
    private final String explosionBarrelsKey;
    private final String UUID_KEY = "uuid";

    private final ItemStack canon = new KitItemBuilder(Material.FIRE_CHARGE).setName("Kanone").setDescription("Abschuss!!").build();
    private final ItemStack remoteDetonator = new KitItemBuilder(Material.TRIPWIRE_HOOK).setName("Fernzünder").setDescription("Explosion!!").build();

    // todo: Barrels als additional kititem adden (3x)
    protected PirateKit() {
        super("Pirate", Material.FIRE_CHARGE);
        Map<KitItemAction, Float> kitActions = Map.of(
                new KitItemAction(canon, "pirate.canon"), 5F,
                new KitItemAction(remoteDetonator, "pirate.remoteDetonator"), 5F
        );
        setItemsAndCooldown(kitActions);
        explosionBarrelsLimit = 3;
        defaultExplosionPower = 3F;
        fireballSpeed = 2;
        explosionBarrelMetaKey = this.getName() + "explosionBarrel";
        explosionBarrelsKey = this.getName() + "explosionBarrelsList";
        maxAdditionalExplosionPower = 5F;
        additionalExplosionPowerStep = 0.5F;
    }

    @KitEvent
    public void onPlayerLeftClicksOneOfMultipleKitItems(PlayerInteractEvent event, KitPlayer kitPlayer, ItemStack item) {
        if (item.isSimilar(remoteDetonator)) {
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
            if (!barrels.isEmpty()) {
                successfulDetonated(Bukkit.getPlayer(kitPlayer.getUUID()), 1);
                detonateExplosionBarrel(barrels.get(0));
                barrels.remove(0);
                kitPlayer.putKitAttribute(explosionBarrelsKey, barrels);
            } else nothingChangedMsg(event.getPlayer());
        }
    }

    @KitEvent
    public void onPlayerRightClicksOneOfMultipleKitItems(PlayerInteractEvent event, KitPlayer kitPlayer, ItemStack item) {
        Player player = event.getPlayer();
        if (item.isSimilar(remoteDetonator)) {
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
            if (barrels.isEmpty()) {
                nothingChangedMsg(player);
                return;
            }
            for (Block barrel : barrels) {
                detonateExplosionBarrel(barrel);
            }
            successfulDetonated(player, barrels.size());
            barrels.clear();
        } else if (item.isSimilar(canon)) {
            player.launchProjectile(Fireball.class, player.getEyeLocation().getDirection().multiply(fireballSpeed));
            this.activateCooldown(kitPlayer, item);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block barrel = event.getBlockPlaced();
        if (barrel.getType() != Material.BARREL) return;
        Player player = event.getPlayer();
        KitPlayer kitPlayer = KitApi.getInstance().getPlayer(player);
        if (kitPlayer.hasKit(this)) {
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, new ArrayList<>());

            if (barrels.size() >= explosionBarrelsLimit) {
                event.setCancelled(true); // produces error, nothing major
                String key = "pirate.limitreached";
                Locale locale = ChatUtils.getPlayerLocale(player);
                ImmutableMap<String, String> arguments = ImmutableMap.of("limit", String.valueOf(explosionBarrelsLimit));
                player.sendMessage(t(key, arguments, locale));
                return;
            }

            barrel.setMetadata(explosionBarrelMetaKey, new FixedMetadataValue(KitApi.getInstance().getPlugin(), ""));
            barrel.setMetadata(UUID_KEY, new FixedMetadataValue(KitApi.getInstance().getPlugin(), kitPlayer.getUUID()));
            barrels.add(barrel);
            kitPlayer.putKitAttribute(explosionBarrelsKey, barrels);

            String key = "pirate.placesuccess";
            Locale locale = ChatUtils.getPlayerLocale(player);
            ImmutableMap<String, String> arguments = ImmutableMap.of("limit", String.valueOf(explosionBarrelsLimit));
            player.sendMessage(t(key, arguments, locale));

        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isExplosionBarrel(block)) {
            UUID owner = (UUID) block.getMetadata(UUID_KEY).get(0).value();
            if (owner == null) return;
            Optional<Player> optionalPlayer = Optional.ofNullable(Bukkit.getPlayer(owner));
            optionalPlayer.ifPresent(player -> {
                String key = "pirate.destroyed";
                Locale locale = ChatUtils.getPlayerLocale(owner);
                ImmutableMap<String, String> arguments = ImmutableMap.of("location", block.getLocation().toString());
                player.sendMessage(t(key, arguments, locale));
                KitPlayer kitPlayer = KitApi.getInstance().getPlayer(player);
                List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
                barrels.removeIf(barrel -> barrel.equals(block));
            });
        }
    }

    // ist verantwortlich für kettenreaktionen =)
    // todo: nur bei einer "linksklick zündung" abfragen, ansonsten explodieren sowieso alle und es geht auch bruch
    // maybe kleinen delay adden, damit es realistischer ist (:
    @EventHandler
    public void onExplosion(BlockExplodeEvent event) {

        List<Block> explosionsBarrels = event.blockList().stream()
                .filter(Objects::nonNull)
                .filter(this::isExplosionBarrel)
                .collect(Collectors.toList());

        explosionsBarrels.forEach(block -> {
            detonateExplosionBarrel(block);
            KitPlayer kitPlayer = KitApi.getInstance().getPlayer(Bukkit.getPlayer((UUID) block.getMetadata(UUID_KEY).get(0).value())); // risky
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
            barrels.removeIf(barrel -> barrel.equals(block));
            kitPlayer.putKitAttribute(explosionBarrelsKey, barrels);
        });

        //event.getBlock().getWorld().getPlayers().get(0).sendMessage("explosionsBarrels: " + explosionsBarrels.size()); // debug
    }

    private void detonateExplosionBarrel(Block block) {
        if (isExplosionBarrel(block)) {
            float explosionPower = defaultExplosionPower + getAdditionalExplosionPower(block);
            float limitedExplosionPower = Math.min(Math.min(explosionPower, maxAdditionalExplosionPower), 100f); // 100f = explosionPower (max)
            block.setType(Material.AIR); // have to be done so the barrel cant be reused!
            block.getWorld().createExplosion(block.getLocation(), limitedExplosionPower, true, true);
            //block.getWorld().getPlayers().get(0).sendMessage("Ich habe ausgelöst."); // debug
        }
    }

    private float getAdditionalExplosionPower(Block block) {
        ItemStack[] invContents = ((Barrel) block.getState()).getInventory().getStorageContents();
        if (invContents == null) return 0;
        int amount = Arrays.stream(invContents)
                .filter(Objects::nonNull)
                .filter(itemStack -> itemStack.getType() == Material.GUNPOWDER)
                .mapToInt(ItemStack::getAmount).sum();
        return Math.min(amount * additionalExplosionPowerStep, maxAdditionalExplosionPower);
    }

    private boolean isExplosionBarrel(Block block) {
        return block.getType() == Material.BARREL && block.hasMetadata(explosionBarrelMetaKey) && block.hasMetadata(UUID_KEY);
    }

    private void nothingChangedMsg(Player player) {
        player.sendMessage(Localization.INSTANCE.getMessage("pirate.nothingChanged", ChatUtils.getPlayerLocale(player)));
    }

    private void successfulDetonated(Player player, Integer amount) {
        String key = "pirate.successfulDetonated";
        Locale locale = ChatUtils.getPlayerLocale(player);
        ImmutableMap<String, String> arguments = ImmutableMap.of("amount", String.valueOf(amount));
        player.sendMessage(t(key, arguments, locale));
    }
}

/* Passt alles, nur die pressure plate wird nicht remove
 Halber Demoman - ist das überhaupt geil für Pirat?
 Anyways.. erstmal nicht removen!!*/

/*    // triggers when a barrel is directly powered from a pressure plate
    @EventHandler
    private void onTriggerExplosionBarrel(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && clickedBlock != null) {
            if (clickedBlock.getType().toString().toLowerCase().endsWith("plate")) {
                Block blockUnder = clickedBlock.getLocation().clone().subtract(0.0, 1.0, 0.0).getBlock();
                if (isExplosionBarrel(blockUnder)) {
                    KitPlayer kitPlayer = KitApi.getInstance().getPlayer(player);
                    List<Block> barrels = kitPlayer.getKitAttribute(explosionBarrelsKey);

                    barrels.remove(blockUnder);
                    kitPlayer.putKitAttribute(explosionBarrelsKey, barrels);

                    clickedBlock.setType(Material.AIR); // lol klappt auch nicht

                    detonateExplosionBarrel(blockUnder);
                    successfulDetonated(player, 1);
                }
            }
        }
    }*/
