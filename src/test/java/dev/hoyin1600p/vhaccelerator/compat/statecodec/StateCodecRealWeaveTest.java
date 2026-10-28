package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.compat.RealWovenFixtures;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.tree.MethodInsnNode;

class StateCodecRealWeaveTest {
    @TestFactory
    Stream<DynamicTest> actualServerExportsAndChangedOriginalBodies() {
        return Stream.of("s4", "wolds").flatMap(site -> Stream.of(
                DynamicTest.dynamicTest(site + " real StateDefinition export", () -> {
                    var node = RealWovenFixtures.read(site, StateCodecLayouts.STATE_DEFINITION);
                    assertEquals(List.of(), StateCodecLayouts.applicationProblems(node));
                }),
                DynamicTest.dynamicTest(site + " changed append body", () -> {
                    var node = RealWovenFixtures.read(site, StateCodecLayouts.STATE_DEFINITION);
                    var append = node.methods.stream().filter(m -> m.desc.equals(StateCodecLayouts.APPEND_DESC)).findFirst().orElseThrow();
                    boolean changed = false;
                    for (var instruction : append.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("xmap")) {
                        call.name = "flatXmap"; changed = true; break;
                    }
                    assertTrue(changed);
                    assertFalse(StateCodecLayouts.applicationProblems(node).isEmpty());
                })));
    }
}
