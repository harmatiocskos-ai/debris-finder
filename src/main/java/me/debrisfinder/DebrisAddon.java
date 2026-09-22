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
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class DebrisAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Debris", Items.ANCIENT_DEBRIS.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules.get().add(new AncientDebrisFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.debrisfinder";
    }

    public static class AncientDebrisFinder extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgRender  = settings.createGroup("Render");

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
            .name("chat-alert").description("Message in chat when something is found.")
            .defaultValue(true).build());

        private final Setting<Integer> maxAlerts = sgGeneral.add(new IntSetting.Builder()
            .name("max-alerts-per-scan").description("Stops chat spam in big clusters.")
            .defaultValue(3).min(1).sliderRange(1, 10).visible(chatAlert::get).build());

        private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound").description("Ping when something is found.")
            .defaultValue(true).visible(chatAlert::get).build());

        private final Setting<Boolean> detectDebris = sgGeneral.add(new BoolSetting.Builder()
            .name("ancient-debris").defaultValue(true).build());

        private final Setting<Boolean> detectGilded = sgGeneral.add(new BoolSetting.Builder()
            .name("gilded-blackstone").defaultValue(true).build());

        private final Setting<Boolean> detectEmerald = sgGeneral.add(new BoolSetting.Builder()
            .name("emerald-ore").description("Detects emerald ore and deepslate emerald ore.")
            .defaultValue(true).build());

        // Ancient debris — orange
        private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").defaultValue(ShapeMode.Both).build());

        private final Setting<SettingColor> debrisSide = sgRender.add(new ColorSetting.Builder()
            .name("debris-side-color").defaultValue(new SettingColor(145, 100, 80, 45)).build());

        private final Setting<SettingColor> debrisLine = sgRender.add(new ColorSetting.Builder()
            .name("debris-line-color").defaultValue(new SettingColor(200, 140, 110, 255)).build());

        // Gilded blackstone — gold
        private final Setting<SettingColor> gildedSide = sgRender.add(new ColorSetting.Builder()
            .name("gilded-side-color").defaultValue(new SettingColor(255, 200, 0, 45)).build());

        private final Setting<SettingColor> gildedLine = sgRender.add(new ColorSetting.Builder()
            .name("gilded-line-color").defaultValue(new SettingColor(255, 210, 0, 255)).build());

        // Emerald ore — green
        private final Setting<SettingColor> emeraldSide = sgRender.add(new ColorSetting.Builder()
            .name("emerald-side-color").defaultValue(new SettingColor(0, 200, 80, 45)).build());

        private final Setting<SettingColor> emeraldLine = sgRender.add(new ColorSetting.Builder()
            .name("emerald-line-color").defaultValue(new SettingColor(0, 255, 100, 255)).build());

        private final Set<BlockPos> foundDebris  = new LinkedHashSet<>();
        private final Set<BlockPos> foundGilded  = new LinkedHashSet<>();
        private final Set<BlockPos> foundEmerald = new LinkedHashSet<>();
        private final Set<BlockPos> announced    = new HashSet<>();
        private int timer;

        public AncientDebrisFinder() {
            super(CATEGORY, "ancient-debris-finder",
                "Highlights ancient debris, gilded blackstone and emerald ore through terrain.");
        }

        @Override
        public void onActivate() {
            foundDebris.clear(); foundGilded.clear(); foundEmerald.clear();
            announced.clear(); timer = 0;
        }

        @Override
        public void onDeactivate() {
            foundDebris.clear(); foundGilded.clear(); foundEmerald.clear(); announced.clear();
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (mc.world == null || mc.player == null) return;
            if (--timer > 0) return;
            timer = interval.get();

            foundDebris.clear(); foundGilded.clear(); foundEmerald.clear();

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
                        var block = mc.world.getBlockState(pos).getBlock();

                        if (detectDebris.get() && block == Blocks.ANCIENT_DEBRIS) {
                            BlockPos bp = pos.toImmutable();
                            foundDebris.add(bp);
                            if (chatAlert.get() && announced.add(bp) && alertsLeft-- > 0) {
                                info("Ancient Debris at (highlight)%d, %d, %d(default) – %.0f blocks away.",
                                    bp.getX(), bp.getY(), bp.getZ(), Math.sqrt(origin.getSquaredDistance(bp)));
                                ping();
                            }
                        }

                        if (detectGilded.get() && block == Blocks.GILDED_BLACKSTONE) {
                            BlockPos bp = pos.toImmutable();
                            foundGilded.add(bp);
                            if (chatAlert.get() && announced.add(bp) && alertsLeft-- > 0) {
                                info("Gilded Blackstone at (gold)%d, %d, %d(default) – %.0f blocks away.",
                                    bp.getX(), bp.getY(), bp.getZ(), Math.sqrt(origin.getSquaredDistance(bp)));
                                ping();
                            }
                        }

                        if (detectEmerald.get() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) {
                            BlockPos bp = pos.toImmutable();
                            foundEmerald.add(bp);
                            if (chatAlert.get() && announced.add(bp) && alertsLeft-- > 0) {
                                info("Emerald Ore at (green)%d, %d, %d(default) – %.0f blocks away.",
                                    bp.getX(), bp.getY(), bp.getZ(), Math.sqrt(origin.getSquaredDistance(bp)));
                                ping();
                            }
                        }
                    }
                }
            }

            Set<BlockPos> all = new HashSet<>(foundDebris);
            all.addAll(foundGilded);
            all.addAll(foundEmerald);
            announced.retainAll(all);
        }

        private void ping() {
            if (!playSound.get()) return;
            mc.world.playSound(mc.player, mc.player.getBlockPos(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1f, 2f);
        }

        @EventHandler
        private void onRender(Render3DEvent event) {
            for (BlockPos pos : foundDebris)
                event.renderer.box(pos, debrisSide.get(), debrisLine.get(), shapeMode.get(), 0);
            for (BlockPos pos : foundGilded)
                event.renderer.box(pos, gildedSide.get(), gildedLine.get(), shapeMode.get(), 0);
            for (BlockPos pos : foundEmerald)
                event.renderer.box(pos, emeraldSide.get(), emeraldLine.get(), shapeMode.get(), 0);
        }

        @Override
        public String getInfoString() {
            return "debris:" + foundDebris.size() + " gilded:" + foundGilded.size() + " emerald:" + foundEmerald.size();
        }
    }
}
