package dev.hoyin1600p.launchfastertoo.mixin.compat.vaulthunters;

import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import dev.hoyin1600p.launchfastertoo.client.compat.vaulthunters.VaultGroupBuild;
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
    private static VaultGroupBuild launchfastertoo$build;

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$stageGroupSetup(
            TickEvent.ClientTickEvent event,
            CallbackInfo ci
    ) {
        if (!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.stagedVaultGroupLoading.get()) {
            launchfastertoo$build = null;
            return;
        }

        ci.cancel();
        if (event.phase != TickEvent.Phase.END || isSetup) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            launchfastertoo$build = null;
            return;
        }

        if (launchfastertoo$build == null
                || !launchfastertoo$build.matchesLevel(level)) {
            launchfastertoo$build = new VaultGroupBuild(level);
            LaunchFasterToo.LOGGER.info("Started staged Vault group construction");
        }

        long budgetNanos =
                LaunchFasterTooClientConfig.VALUES.vaultGroupTickBudgetMillis.get() * 1_000_000L;
        if (!launchfastertoo$build.advance(budgetNanos)) {
            return;
        }

        launchfastertoo$build.publishTo(BLOCK_GROUPS, ENTITY_GROUPS);
        isSetup = true;
        LaunchFasterToo.LOGGER.info(
                "Published {} Vault block groups and {} entity groups after {} ms",
                launchfastertoo$build.blockGroupCount(),
                launchfastertoo$build.entityGroupCount(),
                launchfastertoo$build.elapsedMillis()
        );
        launchfastertoo$build = null;
    }
}
