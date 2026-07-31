package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.ClientWorkSession;
import dev.hoyin1600p.vhaccelerator.client.PostLoginWorkTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.VaultGroupBuild;
import iskallia.vault.core.world.data.entity.EntityPredicate;
import iskallia.vault.util.GroupUtils;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds Vault's lookup maps in bounded main-thread slices and publishes both
 * maps together. No live entity or config predicate is accessed off-thread.
 */
@Mixin(value = GroupUtils.class, remap = false)
public abstract class GroupUtilsMixin {
    @Shadow
    @Final
    public static HashMap<EntityPredicate, Set<EntityType<?>>> ENTITY_GROUPS;

    @Shadow
    @Final
    public static HashMap<ResourceLocation, Set<ResourceLocation>> BLOCK_GROUPS;

    @Shadow
    private static boolean isSetup;

    @Unique
    private static VaultGroupBuild vhaccelerator$build;
    @Unique
    private static long vhaccelerator$workToken;
    @Unique
    private static boolean vhaccelerator$workTracked;

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$stageGroupSetup(
            TickEvent.ClientTickEvent event,
            CallbackInfo ci
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES.stagedVaultGroupLoading.get()) {
            vhaccelerator$cancelBuild();
            return;
        }

        ci.cancel();
        if (event.phase != TickEvent.Phase.END || isSetup) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            vhaccelerator$cancelBuild();
            return;
        }

        if (vhaccelerator$build == null
                || !vhaccelerator$build.matchesLevel(level)) {
            vhaccelerator$cancelBuild();
            vhaccelerator$build = new VaultGroupBuild(level);
            vhaccelerator$workToken =
                    PostLoginWorkTimer.markWorkStarted(
                            ClientWorkSession.current(),
                            "staged Vault group construction"
                    );
            vhaccelerator$workTracked = vhaccelerator$workToken >= 0L;
            VHAccelerator.LOGGER.info("Started staged Vault group construction");
        }

        long budgetNanos =
                VHAcceleratorClientConfig.VALUES.vaultGroupTickBudgetMillis.get() * 1_000_000L;
        if (!vhaccelerator$build.advance(budgetNanos)) {
            return;
        }

        vhaccelerator$build.publishTo(BLOCK_GROUPS, ENTITY_GROUPS);
        isSetup = true;
        VHAccelerator.LOGGER.info(
                "Published {} Vault block groups and {} entity groups after {} ms",
                vhaccelerator$build.blockGroupCount(),
                vhaccelerator$build.entityGroupCount(),
                vhaccelerator$build.elapsedMillis()
        );
        vhaccelerator$build = null;
        vhaccelerator$completeTrackedWork();
    }

    @Unique
    private static void vhaccelerator$cancelBuild() {
        vhaccelerator$build = null;
        if (vhaccelerator$workTracked) {
            PostLoginWorkTimer.cancel(vhaccelerator$workToken);
        }
        vhaccelerator$clearTrackedWork();
    }

    @Unique
    private static void vhaccelerator$completeTrackedWork() {
        if (vhaccelerator$workTracked) {
            PostLoginWorkTimer.markWorkCompleted(vhaccelerator$workToken);
        }
        vhaccelerator$clearTrackedWork();
    }

    @Unique
    private static void vhaccelerator$clearTrackedWork() {
        // Default JVM values represent "no token" so this remains correct if
        // the target is initialized before any Mixin-added code can run.
        vhaccelerator$workToken = 0L;
        vhaccelerator$workTracked = false;
    }
}
