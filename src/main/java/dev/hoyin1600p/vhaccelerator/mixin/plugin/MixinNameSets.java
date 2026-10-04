package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import java.util.Set;

/** Fixed sets of mixin class names that the gating rules refer to. */
final class MixinNameSets {
    /** Mixins that duplicate ModernFix work and are skipped whenever ModernFix is installed. */
    static final Set<String> MODERNFIX_OVERLAPS = Set.of(
            "dev.hoyin1600p.vhaccelerator.mixin.SimpleReloadInstanceMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.ForgeRegistryMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.BlockStateMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.ReloadableResourceManagerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.BlockModelMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryMixin"
    );

    /** Profiling and diagnostics mixins that only apply while debug diagnostics are enabled. */
    static final Set<String> DEBUG_ONLY_MIXINS = Set.of(
            "dev.hoyin1600p.vhaccelerator.mixin.client.ClientModLoaderProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ClientPacketListenerDiagnosticsMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ConnectionProtocolMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.DeferredRegisterProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeHooksClientModelBakeProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeLogoutTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeRecipeEventTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeRegistryAddProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.GameDataRegistryProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.GameRendererTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.LevelRendererTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.MinecraftClearLevelDiagnosticsMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryLoadProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryPreparationProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ReloadListenerProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.TextureAtlasPreparationProfilerMixin"
    );

    private MixinNameSets() {
    }
}
