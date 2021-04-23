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
import de.hglabor.utils.localization.Localization;
import de.hglabor.utils.noriskutils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;


import java.util.*;
import java.util.stream.Collectors;

import static de.hglabor.utils.localization.Localization.t;

public class PirateKit extends MultipleKitItemsKit implements Listener {
    public static final PirateKit INSTANCE = new PirateKit();

    @IntArg
    private final int explosionBarrelsLimit, specSeconds;
    @FloatArg
    private final float detonatorCooldown, canonCooldown;
    @FloatArg(min = 0.1F, max = 100f)
    private final float defaultExplosionPower, additionalExplosionPowerStep, maxAdditionalExplosionPower,
            fireballSpeed; // to add: explosionPower, damage
    @FloatArg
    private final float parrotVelocityMultiplier;


    private final String explosionBarrelMetaKey;
    private final String explosionBarrelsKey;
    private final String UUID_KEY = "uuid";
    private final String PARROT_VARIANT = "parrot_variant";

    private final ItemStack canon = new KitItemBuilder(Material.FIRE_CHARGE).setName("Kanone").setDescription("Abschuss!!").build();
    private final ItemStack remoteDetonator = new KitItemBuilder(Material.TRIPWIRE_HOOK).setName("Fernzünder").setDescription("Explosion!!").build();
    private final ItemStack parrotSpawner = new KitItemBuilder(Material.PARROT_SPAWN_EGG).setName("Tierfreundlich").setDescription("Ich sehe dich").build();

    // todo: Barrels als additional kititem adden (3x)
    protected PirateKit() {
        super("Pirate", Material.FIRE_CHARGE);
        canonCooldown = 30f;
        detonatorCooldown = 5f;
        Map<KitItemAction, Float> kitActions = Map.of(
                new KitItemAction(canon, "pirate.canon"), canonCooldown,
                new KitItemAction(remoteDetonator, "pirate.remoteDetonator"), detonatorCooldown,
                new KitItemAction(parrotSpawner, "pirate.parrotSpawner"), 1f
        );
        setItemsAndCooldown(kitActions);
        explosionBarrelsLimit = 3;
        defaultExplosionPower = 3F;
        fireballSpeed = 2f;
        explosionBarrelMetaKey = this.getName() + "explosionBarrel";
        explosionBarrelsKey = this.getName() + "explosionBarrelsList";
        maxAdditionalExplosionPower = 5F;
        additionalExplosionPowerStep = 0.5F;
        parrotVelocityMultiplier = 1F;
        specSeconds = 10;
    }

    @Override
    public void onEnable(KitPlayer kitPlayer) {
        setShoulderParrot(Bukkit.getPlayer(kitPlayer.getUUID()));
    }

    @Override
    public void onDeactivation(KitPlayer kitPlayer) {
        // disable barrel placing
        // disable detonation
        // disable parrot aktivation & spec
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
        World world = player.getWorld();
        if (item.isSimilar(remoteDetonator)) {
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
            if (barrels.isEmpty()) {
                nothingChangedMsg(player);
                return;
            }
            // sets all explosion barrels to air -> because all the explosion barrels are changed to AIR they will no longer be detected
            // in onBlockExplode and onExplosionPrime also they will no longer destroy anderer barrels um sie herum, ohne dass sie explodieren
            // safes the explosion barrel location and it's explosion power
            HashMap<Location, Float> explosionBarrels = new HashMap<>();
            for (Block block : barrels) {
                if (isExplosionBarrel(block)) {
                    float finalExplosionPower = getFinalExplosionPower(block);
                    block.setType(Material.AIR); // have to be done so the barrel cant be reused!
                    explosionBarrels.put(block.getLocation(), finalExplosionPower);
                }
            }

            // creates explosions at the barrel locations with their explosion power
            for (Location loc : explosionBarrels.keySet()) {
                world.createExplosion(loc, explosionBarrels.get(loc), true, true);
            }

            successfulDetonated(player, barrels.size());
            explosionBarrels.clear();
            barrels.clear();
        } else if (item.isSimilar(canon)) {
            // idea: removing canon projectile (Fireball) after certain amount of time so the server doesn't get lagged
            player.launchProjectile(Fireball.class, player.getEyeLocation().getDirection().multiply(fireballSpeed));
            this.activateCooldown(kitPlayer, item);
        } else if (item.isSimilar(parrotSpawner)) {
            // parrot-spec-ability
        }
    }

    @EventHandler
    public void onCreatureSpawnEvent(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY && event.getEntity().getType() == EntityType.PARROT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) {
            player.sendMessage(Localization.INSTANCE.getMessage("pirate.placefailure", ChatUtils.getPlayerLocale(player)));
            return;
        }
        Block barrel = event.getBlockPlaced();
        if (barrel.getType() != Material.BARREL) return;
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
            this.activateCooldown(kitPlayer, remoteDetonator, true);
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

    // fires when a barrel detonates (this is responsible for chain reactions!)
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        checkExplosion(event.blockList());
    }

    // fires when an entity (tnt, creeper etc.) explodes
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        checkExplosion(event.blockList());
    }

    // detonates and removes all explosion barrels in the blocklist
    private void checkExplosion(List<Block> blockList) {
        List<Block> explosionsBarrels = blockList.stream()
                .filter(Objects::nonNull)
                .filter(this::isExplosionBarrel)
                .collect(Collectors.toList());

        explosionsBarrels.forEach(block -> {
            KitPlayer kitPlayer = KitApi.getInstance().getPlayer(Bukkit.getPlayer((UUID) block.getMetadata(UUID_KEY).get(0).value())); // risky
            List<Block> barrels = kitPlayer.getKitAttributeOrDefault(explosionBarrelsKey, Collections.emptyList());
            barrels.removeIf(barrel -> barrel.equals(block));
            kitPlayer.putKitAttribute(explosionBarrelsKey, barrels);
            detonateExplosionBarrel(block);
        });
    }

    private void detonateExplosionBarrel(Block block) {
        if (isExplosionBarrel(block)) {
            float finalExplosionPower = getFinalExplosionPower(block);
            block.setType(Material.AIR); // have to be done so the barrel cant be reused!
            block.getWorld().createExplosion(block.getLocation(), finalExplosionPower, true, true);
        }
    }

    private float getFinalExplosionPower(Block block) {
        float explosionPower = defaultExplosionPower + getAdditionalExplosionPower(block);
        return Math.min(Math.min(explosionPower, maxAdditionalExplosionPower), 100f);
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

    /*
    Ability: "Parrot Flight" or "Parrot Spec"
    The pirate can fly his parrot;
    - His inventory gets saved and cleared
    - A NPC or something similar spawns at his position to imitate him
    - The pirate gets an Elytra with which he can fly for:
    1. A set amount of Time
    2. A set distance (or both)
    3. Other
    - he gets disguised as a parrot (chameleon like)
    - The parrot (aka the player in parrot-flight-mode) has the following abilities:
    1. A feather with which he can boost himself as a parrot in the direction hes looking
        Params: - boost amount (how often), - boost strength
    2. A item to return to his body
       - the pirate can instantly continue playing
       - the parrot does have cooldown and is flying back to the pirate or something lol
    3. A item to set the parrot as a camera (spawn parrot at his current position in parrot-flight-mode);
       - the pirate can return to the parrot an see through the parrots eyes
       - also, the parrot (automatically?) marks players with a glowing effect (in a radius or (more advanced) if he can see them)
       - if the parrot dies -> cooldown, spawn new on the pirates shoulder
    - the cooldown is made up of:
    1. an set amount
    2. (+) the distance from the position of the parrot to the pirates body
    3. (-) the rest amount of time which the parrot could have been fly
     */

    public void parrotSpecAbility(Player player) {
        World world = player.getWorld();

        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (i >= specSeconds) {
                    this.cancel();
                } else {

                    i++;
                }
            }
        }.runTaskTimer(KitApi.getInstance().getPlugin(), 0L, 20);
    }

    private void prepareParrotFlight(Player player) {
        // clear inv etc.
    }

    public void setShoulderParrot(Player player) {
        Parrot parrot = gimmeParrot(player);
        parrot.remove();
        player.setShoulderEntityRight(parrot);
    }

    private Parrot gimmeParrot(Player player) {
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.PARROT);
        parrot.setOwner(player);
        parrot.setCustomName(player.displayName() + "chen");
        parrot.setVariant(getKitPlayerParrotVariant(player));
        return parrot;
    }

    private Parrot.Variant getKitPlayerParrotVariant(Player player) {
        KitPlayer kitPlayer = KitApi.getInstance().getPlayer(player);
        if (kitPlayer.getKitAttribute(PARROT_VARIANT) == null) {
            kitPlayer.putKitAttribute(PARROT_VARIANT, getRandomParrotVariant());
        }
        return kitPlayer.getKitAttribute(PARROT_VARIANT);
    }

    private Parrot.Variant getRandomParrotVariant() {
        Random random = new Random();
        return Parrot.Variant.values()[random.nextInt(Parrot.Variant.values().length)];
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
