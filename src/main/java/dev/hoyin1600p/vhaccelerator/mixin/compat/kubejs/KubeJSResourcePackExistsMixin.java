package dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.kubejs.KubeJsPackFileIndex;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Answers the KubeJS pack's per-lookup {@code Files.exists} from a listing
 * made once per open pack; see KubeJsPackFileIndex.
 */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.script.data.KubeJSResourcePack", remap = false)
public abstract class KubeJSResourcePackExistsMixin {
    @Unique
    private volatile KubeJsPackFileIndex vhaccelerator$files;
    @Unique
    private volatile boolean vhaccelerator$listed;

    @Redirect(
            method = "m_7211_",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"
            ),
            require = 0
    )
    private boolean vhaccelerator$existsFromListing(Path file, LinkOption[] options) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.indexKubeJsPackFiles, true)) {
            return Files.exists(file, options);
        }
        return KubeJsPackFileIndex.exists(vhaccelerator$index(file), file, options);
    }

    @Inject(method = "close", at = @At("RETURN"), require = 0)
    private void vhaccelerator$dropListing(CallbackInfo callback) {
        synchronized (this) {
            this.vhaccelerator$files = null;
            this.vhaccelerator$listed = false;
        }
    }

    /** The listing of the pack-type folder that contains {@code file}. */
    @Unique
    private KubeJsPackFileIndex vhaccelerator$index(Path file) {
        if (this.vhaccelerator$listed) {
            return this.vhaccelerator$files;
        }
        synchronized (this) {
            if (!this.vhaccelerator$listed) {
                // kubejs/<assets|data>/<namespace>/<path>: list the pack-type folder.
                Path typeRoot = vhaccelerator$typeRoot(file);
                this.vhaccelerator$files = typeRoot == null ? null : KubeJsPackFileIndex.create(typeRoot);
                this.vhaccelerator$listed = true;
            }
            return this.vhaccelerator$files;
        }
    }

    @Unique
    private static Path vhaccelerator$typeRoot(Path file) {
        Path kubejs = Minecraft.getInstance().gameDirectory.toPath().resolve("kubejs").normalize().toAbsolutePath();
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(kubejs) || normalized.getNameCount() <= kubejs.getNameCount()) {
            return null;
        }
        return kubejs.resolve(normalized.getName(kubejs.getNameCount()).toString());
    }
}
