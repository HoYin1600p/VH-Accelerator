package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import net.minecraftforge.fml.loading.LoadingModList;

/** Questions about the early loaded-mod list that need more than a mod-id lookup. */
final class ModPresence {
    private ModPresence() {
    }

    /** Newer Vault builds group modifiers (Modifiers.getModifierGroup) and need no index. */
    static boolean vaultStillScansCascadeStacks(LoadingModList modList) {
        try {
            java.nio.file.Path modifiers = modList.getModFileById("the_vault").getFile()
                    .findResource("iskallia/vault/core/vault/Modifiers.class");
            if (modifiers == null || !java.nio.file.Files.exists(modifiers)) {
                return false;
            }
            String bytes = new String(java.nio.file.Files.readAllBytes(modifiers),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            return !bytes.contains("getModifierGroup");
        } catch (Exception failure) {
            return false;
        }
    }

    /** Whether {@code modId} is loaded at exactly {@code expectedVersion}. */
    static boolean hasVersion(
            LoadingModList modList,
            String modId,
            String expectedVersion
    ) {
        if (modList == null || modList.getModFileById(modId) == null) {
            return false;
        }
        return modList.getMods().stream()
                .filter(mod -> modId.equals(mod.getModId()))
                .anyMatch(mod -> expectedVersion.equals(
                        mod.getVersion().toString()
                ));
    }
}
