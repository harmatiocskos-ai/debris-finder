package me.suschunk;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class SusChunkAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Sus Chunk", Items.ENDER_CHEST.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules.get().add(new SusChunkFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.suschunk";
    }

    public static class SusChunkFinder extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgDetect  = settings.createGroup("Detect");
        private final SettingGroup sgRender  = settings.createGroup("Render");

        private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
            .name("chunk-range")
            .description("How many chunks out to scan around you.")
            .defaultValue(3).min(1).sliderRange(1, 8)
            .build());

        private final Setting<Integer> yLimit = sgGeneral.add(new IntSetting.Builder()
            .name("y-limit")
            .description("Scan blocks below this Y level. Set to 256 to scan all Y levels.")
            .defaultValue(-5).min(-64).sliderRange(-64, 256)
            .build());

        private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
            .name("scan-interval")
            .description("Ticks between scans. Raise if you get lag.")
            .defaultValue(60).min(20).sliderRange(20, 200)
            .build());

        private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-alert")
            .description("Send a chat message when a sus chunk is found.")
            .defaultValue(true)
            .build());

        private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound")
            .description("Ping when a sus chunk is found.")
            .defaultValue(true)
            .visible(chatAlert::get)
            .build());

        private final Setting<Boolean> detectDebris = sgDetect.add(new BoolSetting.Builder()
            .name("ancient-debris")
            .description("Detect ancient debris (Nether and Overworld).")
            .defaultValue(true).build());

        private final Setting<Boolean> detectNetherite = sgDetect.add(new BoolSetting.Builder()
            .name("netherite-block")
            .description("Detect blocks of netherite.")
            .defaultValue(true).build());

        private final Setting<Boolean> detectArmorStands = sgDetect.add(new BoolSetting.Builder()
            .name("armor-stands")
            .description("Detect armor stand entities below the Y limit.")
            .defaultValue(true).build());

        private final Setting<Boolean> detectSpawners = sgDetect.add(new BoolSetting.Builder()
            .name("spawners").defaultValue(true).build());

        private final Setting<Boolean> detectChests = sgDetect.add(new BoolSetting.Builder()
            .name("chests").defaultValue(true).build());

        private final Setting<Boolean> detectEnderChests = sgDetect.add(new BoolSetting.Builder()
            .name("ender-chests").defaultValue(true).build());

        private final Setting<Boolean> detectRedstone = sgDetect.add(new BoolSetting.Builder()
            .name("redstone").defaultValue(true).build());

        private final Setting<Boolean> detectHoppers = sgDetect.add(new BoolSetting.Builder()
            .name("hoppers").defaultValue(true).build());

        private final Setting<Boolean> detectPistons = sgDetect.add(new BoolSetting.Builder()
            .name("pistons").defaultValue(true).build());

        private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").defaultValue(ShapeMode.Lines).build());

        private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").defaultValue(new SettingColor(255, 0, 80, 15)).build());

        private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").defaultValue(new SettingColor(255, 0, 80, 220)).build());

        private final Setting<SettingColor> debrisSideColor = sgRender.add(new ColorSetting.Builder()
            .name("debris-side-color")
            .description("Chunk color for ancient debris.")
            .defaultValue(new SettingColor(255, 160, 0, 15)).build());

        private final Setting<SettingColor> debrisLineColor = sgRender.add(new ColorSetting.Builder()
            .name("debris-line-color")
            .description("Chunk outline for ancient debris.")
            .defaultValue(new SettingColor(255, 160, 0, 220)).build());

        private final Map<ChunkPos, Set<String>> susChunks    = new LinkedHashMap<>();
        private final Map<ChunkPos, Set<String>> debrisChunks = new LinkedHashMap<>();
        private final Set<ChunkPos> announced = new HashSet<>();
        private int timer;

        public SusChunkFinder() {
            super(CATEGORY, "sus-chunk-finder",
                "Finds chunks with suspicious blocks/entities below a Y threshold. Works in any dimension.");
        }

        @Override
        public void onActivate()   { susChunks.clear(); debrisChunks.clear(); announced.clear(); timer = 0; }
        @Override
        public void onDeactivate() { susChunks.clear(); debrisChunks.clear(); announced.clear(); }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (mc.world == null || mc.player == null) return;
            if (--timer > 0) return;
            timer = interval.get();
            scan();
        }

        private void scan() {
            susChunks.clear();
            debrisChunks.clear();

            ChunkPos origin = mc.player.getChunkPos();
            int range   = chunkRange.get();
            int bottomY = mc.world.getBottomY();
            int topY    = Math.min(yLimit.get(), mc.world.getTopYInclusive());

            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int cx = origin.x - range; cx <= origin.x + range; cx++) {
                for (int cz = origin.z - range; cz <= origin.z + range; cz++) {
                    if (!mc.world.isChunkLoaded(cx, cz)) continue;

                    Set<String> triggers  = new LinkedHashSet<>();
                    Set<String> dTriggers = new LinkedHashSet<>();

                    for (int bx = cx * 16; bx < cx * 16 + 16; bx++) {
                        for (int bz = cz * 16; bz < cz * 16 + 16; bz++) {
                            for (int by = bottomY; by < topY; by++) {
                                pos.set(bx, by, bz);
                                Block b = mc.world.getBlockState(pos).getBlock();
                                if (detectDebris.get() && b == Blocks.ANCIENT_DEBRIS) {
                                    dTriggers.add("Ancient Debris");
                                } else {
                                    String name = identifyBlock(b);
                                    if (name != null) triggers.add(name);
                                }
                            }
                        }
                    }

                    ChunkPos cp = new ChunkPos(cx, cz);
                    registerHit(cp, triggers, susChunks);
                    registerHit(cp, dTriggers, debrisChunks);
                }
            }

            if (detectArmorStands.get()) {
                for (var entity : mc.world.getEntities()) {
                    if (!(entity instanceof ArmorStandEntity)) continue;
                    if (entity.getY() >= topY) continue;
                    ChunkPos cp = entity.getChunkPos();
                    if (Math.abs(cp.x - origin.x) > range) continue;
                    if (Math.abs(cp.z - origin.z) > range) continue;
                    susChunks.computeIfAbsent(cp, k -> new LinkedHashSet<>()).add("Armor Stand");
                    if (chatAlert.get() && announced.add(cp)) {
                        info("(highlight)Sus chunk(default) at X:%d Z:%d — Armor Stand",
                            cp.getStartX(), cp.getStartZ());
                        ping();
                    }
                }
            }

            Set<ChunkPos> allFound = new HashSet<>();
            allFound.addAll(susChunks.keySet());
            allFound.addAll(debrisChunks.keySet());
            announced.retainAll(allFound);
        }

        private void registerHit(ChunkPos cp, Set<String> triggers, Map<ChunkPos, Set<String>> map) {
            if (triggers.isEmpty()) return;
            map.put(cp, triggers);
            if (chatAlert.get() && announced.add(cp)) {
                if (map == debrisChunks) {
                    info("(gold)Ancient Debris(default) chunk at X:%d Z:%d", cp.getStartX(), cp.getStartZ());
                } else {
                    info("(highlight)Sus chunk(default) at X:%d Z:%d — %s",
                        cp.getStartX(), cp.getStartZ(), String.join(", ", triggers));
                }
                ping();
            }
        }

        private void ping() {
            if (!playSound.get()) return;
            mc.world.playSound(mc.player, mc.player.getBlockPos(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                SoundCategory.PLAYERS, 1f, 1.5f);
        }

        private String identifyBlock(Block b) {
            if (detectNetherite.get()   && b == Blocks.NETHERITE_BLOCK)                        return "Netherite Block";
            if (detectSpawners.get()    && b == Blocks.SPAWNER)                                return "Spawner";
            if (detectChests.get()      && (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST))  return "Chest";
            if (detectEnderChests.get() && b == Blocks.ENDER_CHEST)                           return "Ender Chest";
            if (detectHoppers.get()     && b == Blocks.HOPPER)                                return "Hopper";
            if (detectPistons.get()     && (b == Blocks.PISTON || b == Blocks.STICKY_PISTON)) return "Piston";
            if (detectRedstone.get()    && isRedstone(b))                                     return "Redstone";
            return null;
        }

        private boolean isRedstone(Block b) {
            return b == Blocks.REDSTONE_WIRE
                || b == Blocks.REDSTONE_TORCH
                || b == Blocks.REDSTONE_WALL_TORCH
                || b == Blocks.COMPARATOR
                || b == Blocks.REPEATER
                || b == Blocks.REDSTONE_BLOCK;
        }

        @EventHandler
        private void onRender(Render3DEvent event) {
            int bottomY = mc.world.getBottomY();
            int topY    = Math.min(yLimit.get(), mc.world.getTopYInclusive());

            for (ChunkPos cp : susChunks.keySet()) {
                int x1 = cp.getStartX(), z1 = cp.getStartZ();
                event.renderer.box(x1, bottomY, z1, x1 + 16, topY, z1 + 16,
                    sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }

            for (ChunkPos cp : debrisChunks.keySet()) {
                int x1 = cp.getStartX(), z1 = cp.getStartZ();
                event.renderer.box(x1, bottomY, z1, x1 + 16, topY, z1 + 16,
                    debrisSideColor.get(), debrisLineColor.get(), shapeMode.get(), 0);
            }
        }

        @Override
        public String getInfoString() {
            return "sus:" + susChunks.size() + " debris:" + debrisChunks.size();
        }
    }
}
