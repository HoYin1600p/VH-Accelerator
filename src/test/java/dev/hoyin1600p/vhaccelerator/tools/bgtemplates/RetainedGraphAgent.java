package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import java.lang.instrument.Instrumentation;

/** Test-JVM shallow-size service; never included in the released mod. */
public final class RetainedGraphAgent {
    private static volatile Instrumentation instrumentation;

    private RetainedGraphAgent() { }

    public static void premain(String ignored, Instrumentation value) {
        instrumentation = value;
    }

    public static Instrumentation instrumentation() {
        Instrumentation result = instrumentation;
        if (result == null) throw new IllegalStateException("The test-only retained-graph javaagent is required");
        return result;
    }
}
