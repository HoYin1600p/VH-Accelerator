package dev.hoyin1600p.vhaccelerator.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LaunchStackSamplerTest {
    private static StackTraceElement frame(String className, String method) {
        return new StackTraceElement(className, method, null, -1);
    }

    @Test
    void attributesTheInnermostModFrame() {
        StackTraceElement[] stack = {
                frame("java.util.zip.Inflater", "inflate"),
                frame("team.chisel.ctm.client.util.TextureMetadataHandler", "onTextureStitch"),
                frame("net.minecraftforge.eventbus.EventBus", "post"),
                frame("net.minecraft.client.renderer.texture.TextureAtlas", "prepareToStitch")
        };
        assertEquals("team.chisel.ctm", LaunchStackSampler.owner(stack));
    }

    @Test
    void attributesVhaHandlersMergedIntoVanillaCode() {
        StackTraceElement[] stack = {
                frame("java.util.HashMap", "get"),
                frame("net.minecraft.client.resources.model.ModelBakery", "vhaccelerator$loadPreloadedModel"),
                frame("net.minecraft.client.resources.model.ModelBakery", "loadBlockModel")
        };
        assertEquals("dev.hoyin1600p.vhaccelerator", LaunchStackSampler.owner(stack));
    }

    @Test
    void labelsOtherMixinHandlersAndPlatformWork() {
        assertEquals("(mixin handler in net.minecraft.client)", LaunchStackSampler.owner(new StackTraceElement[] {
                frame("net.minecraft.client.renderer.texture.TextureAtlas", "handler$zfa000$onStitch")
        }));
        assertEquals("(waiting)", LaunchStackSampler.owner(new StackTraceElement[] {
                frame("jdk.internal.misc.Unsafe", "park"),
                frame("java.util.concurrent.CompletableFuture", "join")
        }));
        assertEquals("(minecraft/forge/jdk)", LaunchStackSampler.owner(new StackTraceElement[] {
                frame("java.util.zip.ZipFile", "getEntry"),
                frame("net.minecraft.client.renderer.texture.TextureAtlas", "lambda$getBasicSpriteInfos$2")
        }));
    }

    @Test
    void doesNotBlameTheLauncherForIdleRenderThreadTime() {
        assertEquals("(minecraft/forge/jdk)", LaunchStackSampler.owner(new StackTraceElement[] {
                frame("org.lwjgl.system.JNI", "invokeV"),
                frame("net.minecraft.client.Minecraft", "m_91398_"),
                frame("io.github.zekerzhayard.forgewrapper.installer.Main", "main"),
                frame("org.prismlauncher.EntryPoint", "main")
        }));
    }

    @Test
    void shortensToThreePackageSegments() {
        assertEquals("com.decocraft.client", LaunchStackSampler.packagePrefix("com.decocraft.client.model.Loader"));
        assertEquals("a.b", LaunchStackSampler.packagePrefix("a.b.C"));
    }
}
