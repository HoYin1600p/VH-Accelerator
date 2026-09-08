package dev.hoyin1600p.vhaccelerator.client.cache;

import com.electronwill.nightconfig.core.file.FileConfig;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

/** Observes reloads without transforming Forge classes already used during bootstrap. */
public final class ClientConfigReloadObserver {
    private ClientConfigReloadObserver() { }

    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            Set<IEventBus> buses = Collections.newSetFromMap(new IdentityHashMap<>());
            ModList.get().forEachModContainer((id, container) -> {
                if (container instanceof FMLModContainer javaMod && buses.add(javaMod.getEventBus())) {
                    javaMod.getEventBus().addListener(ClientConfigReloadObserver::onReload);
                }
            });
            // Initial config loading precedes registration; invalidate any early hash work once.
            LocalConfigState.changed(null);
            if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                VHAccelerator.LOGGER.info("Config fingerprint observer registered on {} Java mod event buses", buses.size());
            }
        });
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        ModConfig config = event.getConfig();
        if (config.getType() == ModConfig.Type.SERVER) { return; }
        LocalConfigState.changed(config.getConfigData() instanceof FileConfig file ? file.getNioPath() : null);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info("Invalidated local config fingerprint after {} config reload for {}",
                    config.getType(), config.getModId());
        }
    }
}
