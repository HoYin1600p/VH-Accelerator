package dev.hoyin1600p.vhaccelerator.mixin.compat.xaero;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.compat.xaero.XaeroOnlineCheckDeferrer;
import java.io.File;
import java.io.IOException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.WorldMap;
import xaero.map.misc.Internet;
import xaero.map.patreon.Patreon;
import xaero.map.settings.ModSettings;

@Mixin(value = WorldMap.class, remap = false)
public abstract class XaeroWorldMapOnlineChecksMixin {
    @Shadow
    public static boolean isOutdated;

    @Shadow
    public static int newestUpdateID;

    @Shadow
    public static String latestVersion;

    @Shadow
    public static String latestVersionMD5;

    @Shadow
    public static File modJAR;

    @Shadow
    public static ModSettings settings;

    @Shadow
    protected abstract String getFileLayoutID();

    @Shadow
    public abstract String getVersionID();

    @Redirect(
            method = "loadClient",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/map/patreon/Patreon;checkPatreon()V"
            )
    )
    private void vhaccelerator$deferOnlineChecks() {
        if (!XaeroOnlineCheckDeferrer.enabled()) {
            Patreon.checkPatreon();
            return;
        }

        XaeroOnlineCheckDeferrer.defer(
                "Xaero's World Map",
                () -> {
                    Patreon.checkPatreon();
                    Internet.checkModVersion();
                },
                this::vhaccelerator$applyOnlineMetadata
        );
    }

    @Redirect(
            method = "loadClient",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/map/misc/Internet;checkModVersion()V"
            )
    )
    private void vhaccelerator$skipDeferredVersionCheck() {
        if (!XaeroOnlineCheckDeferrer.enabled()) {
            Internet.checkModVersion();
        }
    }

    @Unique
    private void vhaccelerator$applyOnlineMetadata() {
        Object entry = Patreon.getMods().get(getFileLayoutID());
        if (!isOutdated || entry == null) {
            return;
        }

        Patreon.setModInfo(
                entry,
                modJAR,
                getVersionID(),
                latestVersion,
                latestVersionMD5,
                () -> {
                    ModSettings.ignoreUpdate = newestUpdateID;
                    try {
                        settings.saveSettings();
                    } catch (IOException exception) {
                        VHAccelerator.LOGGER.error(
                                "Could not save Xaero's World Map ignored update",
                                exception
                        );
                    }
                }
        );
        Patreon.addOutdatedMod(entry);
    }
}
