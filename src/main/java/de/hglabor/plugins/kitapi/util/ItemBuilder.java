package de.hglabor.plugins.kitapi.util;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta itemMeta;

    public ItemBuilder(Material material, short subID) {
        item = new ItemStack(material, 1, subID);
        itemMeta = item.getItemMeta();
    }

    public ItemBuilder(Material material) {
        this(material, (short) 0);
    }

    public ItemBuilder(ItemStack itemStack) {
        item = itemStack;
        itemMeta = item.getItemMeta();
    }

    public ItemBuilder setName(String name) {
        itemMeta.setDisplayName(name);
        return this;
    }

    public ItemBuilder setPlayerSkull(String name) {
        SkullMeta skullMeta = (SkullMeta) itemMeta;
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        return this;
    }

    public ItemBuilder setTitle(String name) {
        BookMeta bookMeta = (BookMeta) itemMeta;
        bookMeta.setTitle(name);
        return this;
    }

    public ItemBuilder addPage(String text) {
        BookMeta bookMeta = (BookMeta) itemMeta;
        bookMeta.addPage(text);
        return this;
    }

    public ItemBuilder setAuthor(String author) {
        BookMeta bookMeta = (BookMeta) itemMeta;
        bookMeta.setAuthor(author);
        return this;
    }

    public ItemBuilder setPage(int page, String text) {
        BookMeta bookMeta = (BookMeta) itemMeta;
        bookMeta.setPage(page, text);
        return this;
    }

    public ItemBuilder setDescription(String... lore) {
        itemMeta.setLore(Arrays.asList(lore));
        return this;
    }

    public ItemBuilder addLore(String... lore) {
        List<String> text = new ArrayList<>();
        text.addAll(Objects.requireNonNull(itemMeta.getLore()));
        text.addAll(Arrays.asList(lore));
        itemMeta.setLore(text);
        return this;
    }

    public ItemBuilder setPotionEffect(PotionEffectType potionEffect, Color color) {
        PotionMeta potionMeta = (PotionMeta) itemMeta;
        potionMeta.setColor(color);
        potionMeta.addCustomEffect(new PotionEffect(potionEffect, 0, 0), true);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder setUnbreakable() {
        itemMeta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder setEnchantment(Enchantment enchantment, int lvl) {
        itemMeta.addEnchant(enchantment, lvl, true);
        return this;
    }

    public ItemBuilder hideItemFlags() {
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(itemMeta);
        return item;
    }
}