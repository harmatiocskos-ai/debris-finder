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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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

    // ------------------------------------------------------------------
    // The module itself.
    // ------------------------------------------------------------------
    public static class AncientDebrisFinder extends Module {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgRender = settings.createGroup("Render");

        private final Setting<Integer> hRange = sgGeneral.add(new IntSetting.Builder()
            .name("horizontal-range")
            .description("How far sideways to scan, in blocks.")
            .defaultValue(32).min(4).sliderRange(4, 64)
            .build()
        );

        private final Setting<Integer> vRange = sgGeneral.add(new IntSetting.Builder()
            .name("vertical-range")
            .description("How far up and down to scan, in blocks.")
            .defaultValue(16).min(2).sliderRange(2, 48)
            .build()
        );

        private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
            .name("scan-interval")
            .description("Ticks between scans. Raise this if you get lag.")
            .defaultValue(20).min(5).sliderRange(5, 100)
            .build()
        );

        private final Setting<Boolean> chatAlert = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-alert")
            .description("Message in chat when new debris comes into range.")
            .defaultValue(true)
            .build()
        );

        private final Setting<Integer> maxAlerts = sgGeneral.add(new IntSetting.Builder()
            .name("max-alerts-per-scan")
            .description("Stops chat spam in a big cluster.")
            .defaultValue(3).min(1).sliderRange(1, 10)
            .visible(chatAlert::get)
            .build()
        );

        private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound")
            .description("Ping when new debris is found.")
            .defaultValue(true)
            .visible(chatAlert::get)
            .build()
        );

        private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
        );

        private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .defaultValue(new SettingColor(145, 100, 80, 45))
            .build()
        );

        private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .defaultValue(new SettingColor(200, 140, 110, 255))
            .build()
        );

        private final Set<BlockPos> found = new LinkedHashSet<>();
        private final Set<BlockPos> announced = new HashSet<>();
        private int timer;

        public AncientDebrisFinder() {
            super(CATEGORY, "ancient-debris-finder", "Highlights ancient debris through terrain and pings you in chat.");
        }

        @Override
        public void onActivate() {
            found.clear();
            announced.clear();
            timer = 0;
        }

        @Override
        public void onDeactivate() {
            found.clear();
            announced.clear();
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (mc.world == null || mc.player == null) return;
            if (--timer > 0) return;
            timer = interval.get();

            found.clear();

            BlockPos origin = mc.player.getBlockPos();
            int h = hRange.get();
            int v = vRange.get();
            int minY = Math.max(mc.world.getBottomY(), origin.getY() - v);
            int maxY = Math.min(mc.world.getTopYInclusive(), origin.getY() + v);

            BlockPos.Mutable pos = new BlockPos.Mutable();
            int alertsLeft = maxAlerts.get();

            for (int x = origin.getX() - h; x <= origin.getX() + h; x++) {
                for (int z = origin.getZ() - h; z <= origin.getZ() + h; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        pos.set(x, y, z);
                        if (mc.world.getBlockState(pos).getBlock() != Blocks.ANCIENT_DEBRIS) continue;

                        BlockPos found1 = pos.toImmutable();
                        found.add(found1);

                        if (chatAlert.get() && announced.add(found1) && alertsLeft > 0) {
                            alertsLeft--;
                            info("Ancient debris at (highlight)%d, %d, %d(default) - %.0f blocks away.",
                                found1.getX(), found1.getY(), found1.getZ(),
                                Math.sqrt(origin.getSquaredDistance(found1)));

                            if (playSound.get()) {
                                mc.world.playSound(mc.player, origin,
                                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                                    SoundCategory.PLAYERS, 1.0f, 2.0f);
                            }
                        }
                    }
                }
            }

            announced.retainAll(found);
        }

        @EventHandler
        private void onRender(Render3DEvent event) {
            for (BlockPos pos : found) {
                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }

        @Override
        public String getInfoString() {
            return String.valueOf(found.size());
        }
    }
}
