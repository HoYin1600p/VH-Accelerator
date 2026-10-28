package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraftforge.api.distmarker.Dist;
import org.junit.jupiter.api.Test;

class VaultLootCdfPolicyTest {
    @Test
    void serverDoesNotLoadOrInvokeClientPermissionAndClientFailsClosed() {
        try {
            VaultLootCdfPolicy.registerClientPermission(() -> { throw new NoClassDefFoundError("unavailable"); });
            assertTrue(VaultLootCdfPolicy.permitsSide(Dist.DEDICATED_SERVER));
            assertFalse(VaultLootCdfPolicy.permitsSide(Dist.CLIENT));
            assertFalse(VaultLootCdfPolicy.permitsSide(null));
            VaultLootCdfPolicy.registerClientPermission(() -> false);
            assertFalse(VaultLootCdfPolicy.permitsSide(Dist.CLIENT));
            VaultLootCdfPolicy.registerClientPermission(() -> true);
            assertTrue(VaultLootCdfPolicy.permitsSide(Dist.CLIENT));
        } finally {
            VaultLootCdfPolicy.registerClientPermission(() -> false);
        }
    }

    @Test
    void unauditedInputsKeepTheOriginalMethod() {
        assertTrue(VaultLootCdfOptimizer.supports(0, 4));
        assertTrue(VaultLootCdfOptimizer.supports(53, 4));
        assertFalse(VaultLootCdfOptimizer.supports(-1, 4));
        assertFalse(VaultLootCdfOptimizer.supports(54, 4));
        assertFalse(VaultLootCdfOptimizer.supports(1, 0));
        assertFalse(VaultLootCdfOptimizer.supports(1, 1));
        assertFalse(VaultLootCdfOptimizer.supports(1, 5));
    }
    @Test
    void clientLazyConstructorCannotSkipOriginalWorkBeforeCommonOwnershipConfirmation() throws Exception {
        String mixin = "dev/hoyin1600p/vhaccelerator/mixin/compat/vaulthunters/TieredLootTableGeneratorCdfMixin.class";
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(mixin)) {
            assertNotNull(input);
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
        }
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("vhaccelerator$deferCompute"))
                .findFirst().orElseThrow();
        var call = java.util.Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode)
                .map(instruction -> (org.objectweb.asm.tree.MethodInsnNode) instruction).findFirst().orElseThrow();
        assertEquals("dev/hoyin1600p/vhaccelerator/compat/CompatMixinGroups", call.owner);
        assertEquals("activationAllowed", call.name);
        var next = call.getNext();
        while (next.getOpcode() < 0) next = next.getNext();
        assertEquals(org.objectweb.asm.Opcodes.IFEQ, next.getOpcode());
        var fallback = ((org.objectweb.asm.tree.JumpInsnNode) next).label;
        boolean original = false;
        for (var instruction = fallback.getNext(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode invoke) {
                assertEquals("compute", invoke.name);
                assertEquals("iskallia/vault/core/world/loot/generator/TieredLootTableGenerator$CDF", invoke.owner);
                original = true;
            }
            assertNotEquals(org.objectweb.asm.Opcodes.ACONST_NULL, instruction.getOpcode(),
                    "unconfirmed ownership must not use the lazy-null branch");
        }
        assertTrue(original);
    }

}
