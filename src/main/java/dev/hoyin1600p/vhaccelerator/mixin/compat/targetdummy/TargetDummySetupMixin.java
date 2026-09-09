package dev.hoyin1600p.vhaccelerator.mixin.compat.targetdummy;

import dev.hoyin1600p.vhaccelerator.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.compat.targetdummy.TargetDummySetupFix;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Corrects only the audited 1.18-1.5.2 parallel common-setup registration. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.dummmmmmy.setup.ModSetup", remap = false)
public abstract class TargetDummySetupMixin {
    @Redirect(
            method = "init(Lnet/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/DispenserBlock;registerBehavior(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/core/dispenser/DispenseItemBehavior;)V",
                    remap = true),
            require = 1,
            allow = 1
    )
    private static void vhaccelerator$queueDispenserRegistration(
            ItemLike item, DispenseItemBehavior behavior, FMLCommonSetupEvent event
    ) {
        boolean debug = BootstrapDebugDiagnostics.enabled();
        if (debug) {
            LogManager.getLogger("VH Accelerator").info(
                    "Target Dummy dispenser registration queued from {}", Thread.currentThread().getName());
        }
        TargetDummySetupFix.defer(event::enqueueWork, () -> {
            DispenserBlock.registerBehavior(item, behavior);
            if (debug) {
                LogManager.getLogger("VH Accelerator").info(
                        "Target Dummy dispenser registration completed on {}", Thread.currentThread().getName());
            }
        });
    }
}
