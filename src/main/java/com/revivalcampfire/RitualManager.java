package com.revivalcampfire;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Logica del ritual en el servidor. El audio y todos los efectos visuales los pone el cliente
 * (ClientRitual / RitualRenderer) a partir del paquete StartRitual; aqui solo vive lo "real":
 * la figura del jugador, el desbaneo y el anuncio.
 *
 * Linea de tiempo (20 ticks = 1 s):  0 inicio ... 571 destello ... 573 desbaneo ...
 * 563-663 la figura desciende del cielo ... 735 fin.
 */
public class RitualManager {
    public static final int TOTAL = 735;
    public static final int UNBAN_TICK = 573;
    public static final int STAND_SPAWN = 563;
    public static final int STAND_LAND = 663;
    private static final Vec3 LAND_OFFSET = new Vec3(-4.0, 0, 2.0);

    private static final List<Ritual> ACTIVE = new ArrayList<>();

    /** Posicion (pies) de la figura segun la edad del ritual. Se usa tambien en el cliente. */
    public static Vec3 figurePos(Vec3 c, float age) {
        float k = Mth.clamp((age - STAND_SPAWN) / (float) (STAND_LAND - STAND_SPAWN), 0f, 1f);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        return new Vec3(c.x + LAND_OFFSET.x, c.y - 0.5 + 28.0 * (1f - e), c.z + LAND_OFFSET.z);
    }

    // ------------------------------------------------------------------ baneados
    private static List<GameProfile> bannedProfiles(MinecraftServer server) {
        UserBanList bans = server.getPlayerList().getBans();
        List<GameProfile> out = new ArrayList<>();
        for (String key : bans.getUserList()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            UserBanListEntry entry = bans.get(new GameProfile(id, null));
            if (entry == null) continue;
            out.add(new GameProfile(id, entry.getDisplayName().getString()));
        }
        return out;
    }

    public static List<ReviveMenu.Entry> banned(MinecraftServer server) {
        List<ReviveMenu.Entry> out = new ArrayList<>();
        for (GameProfile p : bannedProfiles(server)) out.add(new ReviveMenu.Entry(p.getId(), p.getName()));
        out.sort(Comparator.comparing(x -> x.name().toLowerCase()));
        return out;
    }

    private static GameProfile findBanned(MinecraftServer server, UUID id) {
        for (GameProfile p : bannedProfiles(server)) if (id.equals(p.getId())) return p;
        return null;
    }

    public static boolean canUse(ServerPlayer p) {
        return p.server.isSingleplayer() || p.hasPermissions(Config.REQUIRED_PERMISSION.get());
    }

    private static GameProfile fakeProfile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    public static void addTestBan(MinecraftServer server, String name) {
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(new UserBanListEntry(fakeProfile(name), new Date(), "RevivalCampfire", null, "Baneo de prueba"));
        try {
            bans.save();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ inicio
    private static Component begin(ServerLevel level, Vec3 center, GameProfile profile, String reviver) {
        for (Ritual r : ACTIVE) {
            if (r.profile.getId().equals(profile.getId())) {
                return Component.translatable("msg.revivalcampfire.in_progress", profile.getName());
            }
        }
        ACTIVE.add(new Ritual(level, center, profile, reviver));
        return null;
    }

    /** @return null si todo va bien, o el mensaje de error. */
    public static Component start(ServerLevel level, Vec3 center, UUID id, String reviver) {
        GameProfile profile = findBanned(level.getServer(), id);
        if (profile == null) return Component.translatable("msg.revivalcampfire.not_banned", id.toString());
        return begin(level, center, profile, reviver);
    }

    /** Ritual de demostracion con un jugador ficticio. */
    public static Component startDemo(ServerLevel level, Vec3 center, String name, String reviver) {
        return begin(level, center, fakeProfile(name), reviver);
    }

    public static void tick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || ACTIVE.isEmpty()) return;
        Iterator<Ritual> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Ritual r = it.next();
            r.age++;
            if (!r.step(server)) {
                r.cleanup();
                it.remove();
            }
        }
    }

    public static void shutdown() {
        for (Ritual r : ACTIVE) r.cleanup();
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ ritual
    private static class Ritual {
        final ServerLevel level;
        final Vec3 center;
        final GameProfile profile;
        final String reviver;
        int age = 0;
        ArmorStand stand;

        Ritual(ServerLevel level, Vec3 center, GameProfile profile, String reviver) {
            this.level = level;
            this.center = center;
            this.profile = profile;
            this.reviver = reviver;
        }

        boolean step(MinecraftServer server) {
            if (age == 1) {
                Network.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                                new PacketDistributor.TargetPoint(center.x, center.y, center.z, 160, level.dimension())),
                        new Network.StartRitual(center.x, center.y, center.z));
            }
            if (age == STAND_SPAWN) spawnStand();

            if (stand != null && stand.isAlive()) {
                Vec3 p = figurePos(center, age);
                stand.setPos(p.x, p.y, p.z);
                float k = Mth.clamp((age - STAND_SPAWN) / (float) (STAND_LAND - STAND_SPAWN), 0f, 1f);
                if (k < 1f) {
                    stand.setYRot(age * 20f * (1f - k));
                } else {
                    stand.setGlowingTag(false);
                    stand.setYRot((float) Math.toDegrees(Math.atan2(-(center.x - p.x), center.z - p.z)));
                }
            }

            if (age == UNBAN_TICK) {
                UserBanList bans = server.getPlayerList().getBans();
                bans.remove(profile);
                try {
                    bans.save();
                } catch (IOException ignored) {
                }
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("msg.revivalcampfire.broadcast", reviver, profile.getName())
                                .withStyle(ChatFormatting.AQUA), false);
            }
            return age < TOTAL;
        }

        void spawnStand() {
            Vec3 p = figurePos(center, age);
            ArmorStand st = new ArmorStand(level, p.x, p.y, p.z);
            st.setNoGravity(true);
            st.setInvulnerable(true);
            st.setInvisible(true);
            st.setNoBasePlate(true);
            st.setGlowingTag(true);
            st.setCustomName(Component.literal(profile.getName()));
            st.setCustomNameVisible(true);

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), profile));
            st.setItemSlot(EquipmentSlot.HEAD, head);
            st.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
            st.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS));
            st.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS));
            level.addFreshEntity(st);
            this.stand = st;
        }

        private static ItemStack dyed(Item item) {
            ItemStack s = new ItemStack(item);
            ((DyeableLeatherItem) item).setColor(s, 0x101820);
            return s;
        }

        void cleanup() {
            if (stand != null) stand.discard();
        }
    }
}
