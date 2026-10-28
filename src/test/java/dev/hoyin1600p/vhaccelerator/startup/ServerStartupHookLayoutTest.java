package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ServerStartupHookLayoutTest {
    @Test
    void startedEventIsPostedByExactlyOneEventBusCallInHandleServerStarted() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "net/minecraftforge/server/ServerLifecycleHooks.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            int posts = 0;
            for (var method : node.methods) {
                if (!method.name.equals("handleServerStarted")) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("post")
                            && call.owner.equals("net/minecraftforge/eventbus/api/IEventBus")
                            && call.desc.equals("(Lnet/minecraftforge/eventbus/api/Event;)Z")) posts++;
                }
            }
            assertEquals(1, posts, "the redirect that guarantees the report on failure needs exactly one target");
        }
    }

    @Test
    void doneConstantIsUniqueAndPrecedesStartingEventInSupportedForge() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "net/minecraft/server/dedicated/DedicatedServer.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            int done = -1;
            int starting = -1;
            int matches = 0;
            for (var method : node.methods) {
                if (!method.name.equals("initServer")) continue;
                int index = 0;
                for (var instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode constant
                            && "Done ({})! For help, type \"help\"".equals(constant.cst)) {
                        done = index;
                        matches++;
                    }
                    if (instruction instanceof MethodInsnNode call && call.name.equals("handleServerStarting")) {
                        starting = index;
                    }
                    index++;
                }
            }
            assertEquals(1, matches);
            assertTrue(done >= 0 && starting > done);
        }
    }
}
