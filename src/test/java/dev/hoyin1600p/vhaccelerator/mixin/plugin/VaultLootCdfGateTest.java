package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.jar.Manifest;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class VaultLootCdfGateTest {
    private final GateInputs noConfigurationReads = (GateInputs) Proxy.newProxyInstance(
            GateInputs.class.getClassLoader(), new Class<?>[] {GateInputs.class},
            (proxy, method, args) -> { throw new AssertionError("Unexpected input: " + method.getName()); });

    @Test
    void actualArchiveMetadataSelectsTheReviewedCdfHooksAndUnknownVersionsReject() throws Exception {
        try (ZipFile zip = new ZipFile("libs/the_vault-1.18.2-3.21.62.jar")) {
            String version;
            try (var input = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                version = new Manifest(input).getMainAttributes().getValue("Implementation-Version");
            }
            try (var input = zip.getInputStream(zip.getEntry("META-INF/mods.toml"))) {
                assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("version=\"${file.jarVersion}\""));
            }
            assertEquals("1.18.2-3.21.62", version);
            java.util.function.Function<String, byte[]> bytes = name -> {
                try (var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
                    return input.readAllBytes();
                } catch (Exception unavailable) { return null; }
            };
            assertTrue(ModPresence.vaultCdfCompatible(version, bytes));
            assertEquals(List.of(version, VaultLootCdfLayouts.SUPPORTED_VERSION_6884),
                    VaultLootCdfLayouts.contract().get("supportedVersions"));
            for (boolean client : new boolean[] {false, true}) {
                GateFactsBuilder facts = known(client);
                facts.vaultCdfCompatible = ModPresence.vaultCdfCompatible(version, bytes);
                assertTrue(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
                for (String unknown : List.of("3.21.62", "1.18.2-3.21.63", "1.18.2-3.21.62-custom")) {
                    facts.vaultCdfCompatible = ModPresence.vaultCdfCompatible(unknown, bytes);
                    assertFalse(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
                }
            }
            assertFalse(ModPresence.vaultCdfCompatible(version, name -> {
                byte[] content = bytes.apply(name);
                if (name.equals(VaultLootCdfLayouts.CDF)) content[10] ^= 1;
                return content;
            }));
            assertFalse(ModPresence.vaultCdfCompatible(version, name -> null));
        }
    }

    @Test
    void vhapiPresenceVetoesBothCommonAndLegacyHooksEvenWithPristineCompatibleBytes() {
        for (boolean client : new boolean[]{false, true}) {
            GateFactsBuilder facts = known(client);
            facts.vhapiLoaded = true;
            facts.vaultCdfCompatible = true;
            MixinGateRules rules = new MixinGateRules(facts.build(), noConfigurationReads);
            assertFalse(rules.shouldApply(VaultLootCdfLayouts.MIXIN));
            assertFalse(rules.shouldApply("dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.TieredLootTableGeneratorCdfMixin"));
            assertFalse(ModPresence.vaultCdfCompatible("1.18.2-3.21.62", true,
                    name -> { throw new AssertionError("VHAPI veto must precede archive access"); }));
        }
    }

    @Test
    void knownImplementationWeavesOnBothSidesWithoutFreezingTheLiveToggle() {
        for (boolean client : new boolean[] {false, true}) {
            GateFactsBuilder facts = known(client);
            // The common master/Compare toggle must not remove a live-config hook at bootstrap.
            facts.commonMasterEnabled = false;
            assertTrue(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
            facts.vaultCdfCompatible = false;
            assertFalse(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
        }
    }

    @Test
    void unknownDiscoveryAndFutureCommonMixinsFailClosed() {
        GateFactsBuilder facts = known(false);
        facts.modListKnown = false;
        assertFalse(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
        facts = known(false);
        facts.modDiscoveryFailed = true;
        assertFalse(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
        assertFalse(new MixinGateRules(known(true).build(), noConfigurationReads)
                .shouldApply(VaultLootCdfLayouts.GROUP + ".FutureMixin"));
        assertFalse(new MixinGateRules(known(false).build(), noConfigurationReads)
                .shouldApply("dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.TieredLootTableGeneratorCdfMixin"));
    }

    @Test
    void unknownCdfImplementationDoesNotDisableTheIndependentCrystalCorrectnessHook() {
        GateFactsBuilder facts = known(false);
        facts.vaultCdfCompatible = false;
        assertTrue(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(
                "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.CrystalBuddingChunkLoadMixin"));
    }

    @Test
    void commonSettingDefaultsOnAndAppliesToTheNextBuildWithoutRestart() {
        ForgeConfigSpec.ValueSpec setting = VHAcceleratorConfig.COMMON_SPEC.getSpec().get(
                List.of("compatibility", "optimizeVaultLootCdf"));
        assertEquals(Boolean.TRUE, setting.getDefault());
        assertFalse(setting.needsWorldRestart());
        assertTrue(setting.getComment().contains("already-built distributions"));
    }

    private static GateFactsBuilder known(boolean client) {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = client;
        facts.modListKnown = true;
        facts.vaultHuntersLoaded = true;
        facts.vaultCdfCompatible = true;
        return facts;
    }
}
