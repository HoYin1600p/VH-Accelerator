/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_loot_loading/ForgeHooksMixin.java
 * Upstream commit: 0a68e874e98a1476bc36cedfbf2f1e3ee64bcbcb
 * Earlier upstream commit: 0ecee529d7cfb1313c504591609d4ada57efa21a
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; adapted the optimization to Forge 40's patched
 * LootTables lambda, retained ForgeHooks.loadLootTable and its event path,
 * replayed exact source names, and falls back to the original lookup on a miss.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.loot;

import com.google.gson.JsonElement;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.loot.LootResourceOriginCache;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.loot.LootResourceOriginAccess;
import java.io.IOException;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootTables;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTables.class)
public abstract class LootTablesMixin implements LootResourceOriginAccess {
    @Unique
    private LootResourceOriginCache vhaccelerator$lootOrigins;

    @Override
    public LootResourceOriginCache vhaccelerator$lootResourceOrigins() {
        LootResourceOriginCache cache = vhaccelerator$lootOrigins;
        if (cache == null) {
            synchronized (this) {
                cache = vhaccelerator$lootOrigins;
                if (cache == null) {
                    cache = new LootResourceOriginCache();
                    vhaccelerator$lootOrigins = cache;
                }
            }
        }
        return cache;
    }

    @Dynamic("Targets the javac-generated LootTables.apply consumer")
    @Redirect(
            method = "lambda$apply$0("
                    + "Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lcom/google/common/collect/ImmutableMap$Builder;"
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/google/gson/JsonElement;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "getResource(Lnet/minecraft/resources/ResourceLocation;)"
                            + "Lnet/minecraft/server/packs/resources/Resource;"
            )
    )
    private Resource vhaccelerator$reuseResourceOrigin(
            ResourceManager resourceManager,
            ResourceLocation location
    ) throws IOException {
        Resource replay = vhaccelerator$lootResourceOrigins().replay(location);
        return replay != null ? replay : resourceManager.getResource(location);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void vhaccelerator$releaseResourceOrigins(
            Map<ResourceLocation, JsonElement> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        vhaccelerator$lootResourceOrigins().clear();
    }
}
