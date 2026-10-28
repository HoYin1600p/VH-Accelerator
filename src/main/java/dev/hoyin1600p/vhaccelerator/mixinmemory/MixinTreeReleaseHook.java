package dev.hoyin1600p.vhaccelerator.mixinmemory;

import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.MixinService;

/**
 * Runs {@link MixinTreeReleaser} on a dedicated server when the server has started, and once more five
 * minutes later for the targets that loaded in between. Off unless {@code releaseMixinClassTrees} is on.
 */
public final class MixinTreeReleaseHook {
    static final int LATE_SWEEP_TICKS = 6000;

    private static final LateSweepSchedule SCHEDULE = new LateSweepSchedule(LATE_SWEEP_TICKS);

    private MixinTreeReleaseHook() {
    }

    public static void serverStarted(Logger logger) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || !VHAcceleratorConfig.releaseMixinClassTreesEnabled()) {
            return;
        }
        if (sweep(logger, "after server start")) {
            SCHEDULE.arm();
        }
    }

    public static void serverTick(TickEvent.ServerTickEvent event, Logger logger) {
        if (SCHEDULE.tick(event.phase == TickEvent.Phase.END)) {
            sweep(logger, "5 minutes after server start");
        }
    }

    private static boolean sweep(Logger logger, String when) {
        try {
            MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
            MixinTreeAccess.Result opened = MixinTreeAccess.open(
                    environment.getVersion(),
                    environment.getOption(MixinEnvironment.Option.HOT_SWAP),
                    environment.getActiveTransformer()
            );
            if (opened.access() == null) {
                logger.warn("Mixin class-tree release skipped, original behaviour kept: {}", opened.failure());
                return false;
            }
            IClassTracker tracker = MixinService.getService().getClassTracker();
            if (tracker == null) {
                logger.warn("Mixin class-tree release skipped, original behaviour kept: Mixin has no class tracker");
                return false;
            }
            MixinTreeReleaser.Statistics statistics = MixinTreeReleaser.sweep(opened.access(), tracker::isClassLoaded);
            logger.info(
                    "Mixin class-tree release ({}): {} of {} mixins released ({} KiB of class files kept as {} KiB), "
                            + "{} already released, {} kept for unloaded targets, {} kept as pseudo or untargeted, "
                            + "{} without state, {} not byte-stable, {} failed, {} ms",
                    when,
                    statistics.released(),
                    statistics.mixins(),
                    statistics.classBytes() / 1024,
                    statistics.storedBytes() / 1024,
                    statistics.alreadyReleased(),
                    statistics.keptUnloadedTargets(),
                    statistics.keptPseudoOrUntargeted(),
                    statistics.keptNoState(),
                    statistics.keptUnstable(),
                    statistics.failed(),
                    statistics.millis()
            );
            return true;
        } catch (Throwable throwable) {
            logger.warn("Mixin class-tree release failed, original behaviour kept: {}", throwable.toString());
            return false;
        }
    }
}
