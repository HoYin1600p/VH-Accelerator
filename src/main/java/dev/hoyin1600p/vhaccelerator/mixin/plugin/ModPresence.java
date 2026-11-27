package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import net.minecraftforge.fml.loading.LoadingModList;

/** Questions about the early loaded-mod list that need more than a mod-id lookup. */
final class ModPresence {
    private ModPresence() {
    }

    static boolean worldZonesCompatible(LoadingModList modList) {
        try {
            if (modList == null || modList.getModFileById("the_vault") == null) return false;
            String version = modList.getMods().stream().filter(mod -> "the_vault".equals(mod.getModId()))
                    .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
            return dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.supports(version, name -> {
                try {
                    var path = modList.getModFileById("the_vault").getFile().findResource(name + ".class");
                    return path != null && java.nio.file.Files.isRegularFile(path) ? java.nio.file.Files.readAllBytes(path) : null;
                } catch (Exception | LinkageError failure) { return null; }
            });
        } catch (RuntimeException | LinkageError failure) { return false; }
    }

    /** Presence is ownership: never compete with VHAPI even before its transforms run. */
    static boolean vaultCdfCompatible(LoadingModList modList) {
        try {
            if (modList == null || modList.getModFileById("the_vault") == null
                    || modList.getModFileById("vhapi") != null) return false;
            String version = modList.getMods().stream().filter(mod -> "the_vault".equals(mod.getModId()))
                    .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
            return vaultCdfCompatible(version, false, name -> {
                try {
                    java.nio.file.Path path = modList.getModFileById("the_vault").getFile()
                            .findResource(name + ".class");
                    return path == null || !java.nio.file.Files.isRegularFile(path)
                            ? null : java.nio.file.Files.readAllBytes(path);
                } catch (Exception | LinkageError unavailable) { return null; }
            });
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    /**
     * The lazy, compact distribution composes with VHAPI, so presence of VHAPI does not veto it here;
     * the final merged-owner verification decides who may own compute and getProbability.
     */
    static boolean vaultCdfLazyCompatible(LoadingModList modList) {
        try {
            if (modList == null || modList.getModFileById("the_vault") == null) return false;
            String version = modList.getMods().stream().filter(mod -> "the_vault".equals(mod.getModId()))
                    .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
            return dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.supports(version, name -> {
                try {
                    java.nio.file.Path path = modList.getModFileById("the_vault").getFile()
                            .findResource(name + ".class");
                    return path == null || !java.nio.file.Files.isRegularFile(path)
                            ? null : java.nio.file.Files.readAllBytes(path);
                } catch (Exception | LinkageError unavailable) { return null; }
            });
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    static boolean vaultCdfCompatible(String version, java.util.function.Function<String, byte[]> bytes) {
        return vaultCdfCompatible(version, false, bytes);
    }

    /** Shared production predicate; discovered metadata is never shortened or rewritten. */
    static boolean vaultCdfCompatible(String version, boolean vhapiPresent,
            java.util.function.Function<String, byte[]> bytes) {
        return !vhapiPresent && dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.supports(version, bytes);
    }

    /**
     * Vault 22.0.0 caches its listeners in firing order (Event.orderedListeners, invalidated
     * on every add and release, ForgeEvent included), so VHA's dispatch snapshot is redundant.
     */
    static boolean vaultEventDispatchCopies(LoadingModList modList) {
        try {
            java.nio.file.Path event = modList.getModFileById("the_vault").getFile()
                    .findResource("iskallia/vault/core/event/Event.class");
            if (event == null || !java.nio.file.Files.exists(event)) {
                return false;
            }
            return eventDispatchCopies(java.nio.file.Files.readAllBytes(event));
        } catch (Exception failure) {
            return false;
        }
    }

    /** Whether Vault's Event bytecode lacks its own listener cache. */
    static boolean eventDispatchCopies(byte[] eventClass) {
        return !new String(eventClass, java.nio.charset.StandardCharsets.ISO_8859_1).contains("orderedListeners");
    }

    /** Newer Vault builds index modifiers (Modifiers.getModifierGroup or Modifiers.stacksOf) and need no index. */
    static boolean vaultStillScansCascadeStacks(LoadingModList modList) {
        try {
            java.nio.file.Path modifiers = modList.getModFileById("the_vault").getFile()
                    .findResource("iskallia/vault/core/vault/Modifiers.class");
            if (modifiers == null || !java.nio.file.Files.exists(modifiers)) {
                return false;
            }
            return cascadeStacksScanned(java.nio.file.Files.readAllBytes(modifiers));
        } catch (Exception failure) {
            return false;
        }
    }

    /** Whether Vault's Modifiers bytecode lacks its own modifier stack index. */
    static boolean cascadeStacksScanned(byte[] modifiersClass) {
        String bytes = new String(modifiersClass, java.nio.charset.StandardCharsets.ISO_8859_1);
        return !bytes.contains("getModifierGroup") && !bytes.contains("stacksOf");
    }

    /** Vault 22.0.1-7237+ cancels ItemStack.setEntityRepresentation on empty stacks directly in MixinItemStack. */
    static boolean vaultEmptyItemStackFixed(LoadingModList modList) {
        try {
            if (modList == null || modList.getModFileById("the_vault") == null) {
                return false;
            }
            java.nio.file.Path mixin = modList.getModFileById("the_vault").getFile()
                    .findResource("iskallia/vault/mixin/MixinItemStack.class");
            if (mixin == null || !java.nio.file.Files.exists(mixin)) {
                return false;
            }
            return emptyItemStackFixed(java.nio.file.Files.readAllBytes(mixin));
        } catch (Exception failure) {
            return false;
        }
    }

    /** Whether Vault's MixinItemStack already blocks entity links on empty stacks. */
    static boolean emptyItemStackFixed(byte[] mixinItemStackClass) {
        return new String(mixinItemStackClass, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("keepEmptyStacksFreeOfEntityLinks");
    }

    /** The data-owner optimization admits one reviewed physical archive and Guava layout. */
    static boolean buildingGadgetsTemplatesCompatible(LoadingModList modList) {
        if (!hasVersion(modList, "buildinggadgets",
                dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateLayouts.VERSION)) return false;
        try {
            return dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateLayouts.supportsArchive(
                    modList.getModFileById("buildinggadgets").getFile().getFilePath(),
                    dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateLayouts.VERSION);
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    /** Whether the mod file of {@code modId} contains the class resource {@code path}; failures mean false. */
    static boolean hasResource(LoadingModList modList, String modId, String path) {
        try {
            if (modList == null || modList.getModFileById(modId) == null) {
                return false;
            }
            java.nio.file.Path found = modList.getModFileById(modId).getFile().findResource(path);
            return found != null && java.nio.file.Files.exists(found);
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
