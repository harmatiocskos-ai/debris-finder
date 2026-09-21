package me.debrisfinder;

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
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

public class DebrisAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Debris", Items.ANCIENT_DEBRIS.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules.get().add(new AncientDebrisFinder());
        Modules.get().add(new SusChunkFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.debrisfinder";
    }

    // ===================================================================
    // Module 1: Ancient Debris Finder
    // ===================================================================
    public static class AncientDebrisFinder extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgRender   = settings.createGroup("Render");

        private final Setting<Integer> hRange = sgGeneral.add(new IntSetting.Builder()
            .name("horizontal-range").description("How far sideways to scan, in blocks.")
            .defaultValue(32).min(4).sliderRange(4, 64).build());

        private final Setting<Integer> vRange = sgGeneral.add(new IntSetting.Builder()
            .name("vertical-range").description("How far up and down to scan, in blocks.")
            .defaultValue(16).min(2).sliderRange(2, 48).build());

        private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
            .name("scan-interval").description("Ticks between scans.")
            .defaultValue(20).min(5).sliderRange(5, 100).build());

        private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-alert").description("Message in chat when new debris is found.")
            .defaultValue(true).build());

        private final Setting<Integer> maxAlerts = sgGeneral.add(new IntSetting.Builder()
            .name("max-alerts-per-scan").description("Stops chat spam in big clusters.")
            .defaultValue(3).min(1).sliderRange(1, 10).visible(chatAlert::get).build());

        private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound").description("Ping when new debris is found.")
            .defaultValue(true).visible(chatAlert::get).build());

        private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").defaultValue(ShapeMode.Both).build());

        private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").defaultValue(new SettingColor(145, 100, 80, 45)).build());

        private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").defaultValue(new SettingColor(200, 140, 110, 255)).build());

        private final Set<BlockPos> found     = new LinkedHashSet<>();
        private final Set<BlockPos> announced = new HashSet<>();
        private int timer;

        public AncientDebrisFinder() {
            super(CATEGORY, "ancient-debris-finder", "Highlights ancient debris through terrain and alerts in chat.");
        }

        @Override public void onActivate()   { found.clear(); announced.clear(); timer = 0; }
        @Override public void onDeactivate() { found.clear(); announced.clear(); }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (mc.world == null || mc.player == null) return;
            if (--timer > 0) return;
            timer = interval.get();

            found.clear();
            BlockPos origin = mc.player.getBlockPos();
            int h = hRange.get(), v = vRange.get();
            int minY = Math.max(mc.world.getBottomY(), origin.getY() - v);
            int maxY = Math.min(mc.world.getTopYInclusive(), origin.getY() + v);
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int alertsLeft = maxAlerts.get();

            for (int x = origin.getX() - h; x <= origin.getX() + h; x++) {
                for (int z = origin.getZ() - h; z <= origin.getZ() + h; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        pos.set(x, y, z);
                        if (mc.world.getBlockState(pos).getBlock() != Blocks.ANCIENT_DEBRIS) continue;
                        BlockPos bp = pos.toImmutable();
                        found.add(bp);
                        if (chatAlert.get() && announced.add(bp) && alertsLeft-- > 0) {
                            info("Ancient debris at (highlight)%d, %d, %d(default) – %.0f blocks away.",
                                bp.getX(), bp.getY(), bp.getZ(),
                                Math.sqrt(origin.getSquaredDistance(bp)));
                            if (playSound.get())
                                mc.world.playSound(mc.player, origin,
                                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                                    SoundCategory.PLAYERS, 1f, 2f);
                        }
                    }
                }
            }
            announced.retainAll(found);
        }

        @EventHandler
        private void onRender(Render3DEvent event) {
            for (BlockPos pos : found)
                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }

        @Override public String getInfoString() { return String.valueOf(found.size()); }
    }

    // ===================================================================
    // Module 2: Sus Chunk Finder
    // ===================================================================
    public static class SusChunkFinder extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgDetect  = settings.createGroup("Detect");
        private final SettingGroup sgRender  = settings.createGroup("Render");

        private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
            .name("chunk-range").description("Chunks to scan around you.")
            .defaultValue(3).min(1).sliderRange(1, 8).build());

        private final Setting<Integer> yLimit = sgGeneral.add(new IntSetting.Builder()
            .name("y-limit").description("Scan blocks below this Y level.")
            .defaultValue(-5).min(-64).sliderRange(-64, 64).build());

        private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
            .name("scan-interval").description("Ticks between scans.")
            .defaultValue(60).min(20).sliderRange(20, 200).build());

        private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-alert").description("Message in chat when a sus chunk is found.")
            .defaultValue(true).build());

        private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound").description("Ping when a sus chunk is found.")
            .defaultValue(true).visible(chatAlert::get).build());

        // Block toggles
        private final Setting<Boolean> detectSpawners    = sgDetect.add(new BoolSetting.Builder().name("spawners").defaultValue(true).build());
        private final Setting<Boolean> detectChests      = sgDetect.add(new BoolSetting.Builder().name("chests").defaultValue(true).build());
        private final Setting<Boolean> detectEnderChests = sgDetect.add(new BoolSetting.Builder().name("ender-chests").defaultValue(true).build());
        private final Setting<Boolean> detectRedstone    = sgDetect.add(new BoolSetting.Builder().name("redstone").defaultValue(true).build());
        private final Setting<Boolean> detectHoppers     = sgDetect.add(new BoolSetting.Builder().name("hoppers").defaultValue(true).build());
        private final Setting<Boolean> detectPistons     = sgDetect.add(new BoolSetting.Builder().name("pistons").defaultValue(true).build());

        // Render
        private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").defaultValue(ShapeMode.Lines).build());

        private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").defaultValue(new SettingColor(255, 0, 80, 15)).build());

        private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").defaultValue(new SettingColor(255, 0, 80, 220)).build());

        private final Map<ChunkPos, Set<String>> susChunks = new LinkedHashMap<>();
        private final Set<ChunkPos>              announced = new HashSet<>();
        private int timer;

        public SusChunkFinder() {
            super(CATEGORY, "sus-chunk-finder",
                "Highlights chunks with player-placed blocks (chests, spawners, redstone, hoppers, pistons) below a Y level.");
        }

        @Override public void onActivate()   { susChunks.clear(); announced.clear(); timer = 0; }
        @Override public void onDeactivate() { susChunks.clear(); announced.clear(); }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (mc.world == null || mc.player == null) return;
            if (--timer > 0) return;
            timer = interval.get();
            scan();
        }

        private void scan() {
            susChunks.clear();
            ChunkPos origin = mc.player.getChunkPos();
            int range   = chunkRange.get();
            int bottomY = mc.world.getBottomY();
            int topY    = yLimit.get();

            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int cx = origin.x - range; cx <= origin.x + range; cx++) {
                for (int cz = origin.z - range; cz <= origin.z + range; cz++) {
                    if (!mc.world.isChunkLoaded(cx, cz)) continue;

                    Set<String> triggers = new LinkedHashSet<>();
                    for (int bx = cx * 16; bx < cx * 16 + 16; bx++) {
                        for (int bz = cz * 16; bz < cz * 16 + 16; bz++) {
                            for (int by = bottomY; by < topY; by++) {
                                pos.set(bx, by, bz);
                                String name = identify(mc.world.getBlockState(pos).getBlock());
                                if (name != null) triggers.add(name);
                            }
                        }
                    }

                    if (!triggers.isEmpty()) {
                        ChunkPos cp = new ChunkPos(cx, cz);
                        susChunks.put(cp, triggers);
                        if (chatAlert.get() && announced.add(cp)) {
                            info("(highlight)Sus chunk(default) at X:%d Z:%d — %s",
                                cx * 16, cz * 16, String.join(", ", triggers));
                            if (playSound.get())
                                mc.world.playSound(mc.player, mc.player.getBlockPos(),
                                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                                    SoundCategory.PLAYERS, 1f, 1.5f);
                        }
                    }
                }
            }
            announced.retainAll(susChunks.keySet());
        }

        private String identify(Block b) {
            if (detectSpawners.get()    && b == Blocks.SPAWNER)                                              return "Spawner";
            if (detectChests.get()      && (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST))                return "Chest";
            if (detectEnderChests.get() && b == Blocks.ENDER_CHEST)                                         return "Ender Chest";
            if (detectHoppers.get()     && b == Blocks.HOPPER)                                              return "Hopper";
            if (detectPistons.get()     && (b == Blocks.PISTON || b == Blocks.STICKY_PISTON))               return "Piston";
            if (detectRedstone.get()    && isRedstone(b))                                                    return "Redstone";
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
            if (susChunks.isEmpty()) return;
            int bottomY = mc.world.getBottomY();
            int topY    = yLimit.get();
            for (ChunkPos cp : susChunks.keySet()) {
                int x1 = cp.getStartX(), z1 = cp.getStartZ();
                event.renderer.box(x1, bottomY, z1, x1 + 16, topY, z1 + 16,
                    sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }

        @Override public String getInfoString() { return String.valueOf(susChunks.size()); }
    }
}
