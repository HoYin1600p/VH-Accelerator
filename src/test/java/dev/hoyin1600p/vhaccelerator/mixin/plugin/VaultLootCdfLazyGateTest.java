package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class VaultLootCdfLazyGateTest {
    private static final String LAZY = VaultLootCdfLayouts.LAZY_MIXIN;
    private final GateInputs noConfigurationReads = (GateInputs) Proxy.newProxyInstance(
            GateInputs.class.getClassLoader(), new Class<?>[] {GateInputs.class},
            (proxy, method, args) -> { throw new AssertionError("Unexpected input: " + method.getName()); });

    private static GateFactsBuilder known(boolean client) {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = client;
        facts.modListKnown = true;
        facts.vaultHuntersLoaded = true;
        facts.vaultCdfLazyCompatible = true;
        return facts;
    }

    private boolean applies(GateFactsBuilder facts) {
        return new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(LAZY);
    }

    @Test
    void hashEqualityAdmitsBothReviewedVersionsFromTheirRealArchivesAndNothingElse() throws Exception {
        int accepted = 0;
        for (String archive : List.of("the_vault-1.18.2-3.21.62.jar", "the_vault-1.18.2-3.21.6.6884.jar")) {
            try (ZipFile zip = new ZipFile(Path.of("libs", archive).toFile())) {
                String version;
                try (var input = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                    version = new Manifest(input).getMainAttributes().getValue("Implementation-Version");
                }
                Function<String, byte[]> bytes = name -> {
                    try (var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
                        return input.readAllBytes();
                    } catch (Exception unavailable) { return null; }
                };
                assertTrue(VaultLootCdfLayouts.supports(version, bytes), version);
                assertTrue(ModPresence.vaultCdfCompatible(version, bytes), "the eager hook shares the version gate: " + version);
                for (String other : List.of("3.21.6.6884", "1.18.2-3.21.6.6885", "1.18.2-3.21.62-custom", "1.18.2-3.21.63", "")) {
                    assertFalse(VaultLootCdfLayouts.supports(other, bytes), other);
                }
                assertFalse(VaultLootCdfLayouts.supports(version, name -> {
                    byte[] content = bytes.apply(name);
                    if (name.equals(VaultLootCdfLayouts.CDF)) content[content.length - 1] ^= 1;
                    return content;
                }), "same version string, changed bytes");
                assertFalse(VaultLootCdfLayouts.supports(version, name -> null));
                accepted++;
            }
        }
        assertEquals(2, accepted);
        assertEquals("1.18.2-3.21.6.6884", VaultLootCdfLayouts.SUPPORTED_VERSION_6884);
        assertEquals(List.of(VaultLootCdfLayouts.SUPPORTED_VERSION, VaultLootCdfLayouts.SUPPORTED_VERSION_6884),
                VaultLootCdfLayouts.lazyContract().get("supportedVersions"));
        assertEquals(List.of(VaultLootCdfLayouts.SUPPORTED_VERSION, VaultLootCdfLayouts.SUPPORTED_VERSION_6884),
                VaultLootCdfLayouts.contract().get("supportedVersions"));
    }

    @Test
    void dedicatedServersSelectItEvenWithVhapiAndClientsNever() {
        assertTrue(applies(known(false)));
        GateFactsBuilder withVhapi = known(false);
        withVhapi.vhapiLoaded = true;
        assertTrue(applies(withVhapi), "composes with VHAPI");
        withVhapi.vaultCdfCompatible = false;
        assertTrue(applies(withVhapi), "independent of the eager hook's VHAPI veto");
        assertFalse(applies(known(true)), "physical clients keep the client lazy hook");
        GateFactsBuilder master = known(false);
        master.commonMasterEnabled = false;
        assertTrue(applies(master), "the live toggle is read at construction, not frozen at bootstrap");
    }

    @Test
    void unknownDiscoveryIncompatibleLayoutAndFutureMixinsFailClosed() {
        GateFactsBuilder facts = known(false);
        facts.modListKnown = false;
        assertFalse(applies(facts));
        facts = known(false);
        facts.modDiscoveryFailed = true;
        assertFalse(applies(facts));
        facts = known(false);
        facts.vaultCdfLazyCompatible = false;
        assertFalse(applies(facts));
        assertFalse(new MixinGateRules(known(false).build(), noConfigurationReads)
                .shouldApply(VaultLootCdfLayouts.LAZY_GROUP + ".FutureMixin"));
        // The eager hook is still vetoed by VHAPI.
        facts = known(false);
        facts.vhapiLoaded = true;
        facts.vaultCdfCompatible = true;
        assertFalse(new MixinGateRules(facts.build(), noConfigurationReads).shouldApply(VaultLootCdfLayouts.MIXIN));
    }

    @Test
    void groupIsCommonSeparateFromTheEagerGroupAndWithholdsActivation() {
        assertNotEquals(VaultLootCdfLayouts.GROUP, VaultLootCdfLayouts.LAZY_GROUP);
        assertEquals(VaultLootCdfLayouts.LAZY_GROUP, LAZY.substring(0, LAZY.lastIndexOf('.')));
        assertTrue(CommonCompatibilityLayouts.isCommonGroup(VaultLootCdfLayouts.LAZY_GROUP));
        assertTrue(CommonCompatibilityLayouts.isCommonMixin(LAZY));
        assertFalse(CommonCompatibilityLayouts.isCommonMixin(VaultLootCdfLayouts.LAZY_GROUP + ".FutureMixin"));
        assertFalse(CompatMixinGroups.activationAllowed(LAZY), "nothing is active before application is confirmed");
        assertEquals("compatibility.lazyCompactVaultLootCdf", CommonCompatibilityLayouts.contract(
                VaultLootCdfLayouts.LAZY_GROUP).get("setting"));
    }

    @Test
    void commonSettingDefaultsOnAndAppliesToTheNextBuildWithoutRestart() {
        ForgeConfigSpec.ValueSpec setting = VHAcceleratorConfig.COMMON_SPEC.getSpec().get(
                List.of("compatibility", "lazyCompactVaultLootCdf"));
        assertEquals(Boolean.TRUE, setting.getDefault());
        assertFalse(setting.needsWorldRestart());
        assertTrue(setting.getComment().contains("VHAPI"));
    }
}
