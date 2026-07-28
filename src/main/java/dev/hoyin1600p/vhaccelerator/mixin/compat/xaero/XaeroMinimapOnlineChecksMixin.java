package dev.hoyin1600p.vhaccelerator.mixin.compat.xaero;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroOnlineCheckDeferrer;
import java.io.IOException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.common.HudMod;
import xaero.common.IXaeroMinimap;
import xaero.common.misc.Internet;
import xaero.common.patreon.Patreon;
import xaero.common.patreon.PatreonMod;
import xaero.common.settings.ModSettings;

@Mixin(value = HudMod.class, remap = false)
public abstract class XaeroMinimapOnlineChecksMixin {
    @Redirect(
            method = "loadLater",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/common/patreon/Patreon;checkPatreon(Lxaero/common/IXaeroMinimap;)V"
            )
    )
    private void vhaccelerator$deferOnlineChecks(
            IXaeroMinimap minimap
    ) {
        if (!XaeroOnlineCheckDeferrer.enabled()) {
            Patreon.checkPatreon(minimap);
            return;
        }

        XaeroOnlineCheckDeferrer.defer(
                "Xaero's Minimap",
                () -> {
                    Patreon.checkPatreon(minimap);
                    Internet.checkModVersion(minimap);
                },
                () -> vhaccelerator$applyOnlineMetadata(minimap)
        );
    }

    @Redirect(
            method = "loadLater",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/common/misc/Internet;checkModVersion(Lxaero/common/IXaeroMinimap;)V"
            )
    )
    private void vhaccelerator$skipDeferredVersionCheck(
            IXaeroMinimap minimap
    ) {
        if (!XaeroOnlineCheckDeferrer.enabled()) {
            Internet.checkModVersion(minimap);
        }
    }

    @Unique
    private static void vhaccelerator$applyOnlineMetadata(
            IXaeroMinimap minimap
    ) {
        PatreonMod entry = minimap.getPatreon();
        if (!minimap.isOutdated() || entry == null) {
            return;
        }

        entry.modJar = minimap.getModJAR();
        entry.currentVersion = minimap.getVersionID();
        entry.latestVersion = minimap.getLatestVersion();
        entry.md5 = minimap.getLatestVersionMD5();
        entry.onVersionIgnore = () -> {
            ModSettings.ignoreUpdate = minimap.getNewestUpdateID();
            try {
                minimap.getSettings().saveSettings();
            } catch (IOException exception) {
                VHAccelerator.LOGGER.error(
                        "Could not save Xaero's Minimap ignored update",
                        exception
                );
            }
        };
        Patreon.addOutdatedMod(entry);
    }
}
