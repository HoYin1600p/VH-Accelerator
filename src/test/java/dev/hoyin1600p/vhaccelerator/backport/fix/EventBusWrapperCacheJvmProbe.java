package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Fresh-JVM verification entry point, not included in the mod or its sources jar. */
public final class EventBusWrapperCacheJvmProbe {
    public static final class FixtureEvent extends Event { }
    public static final class Fixture {
        static final AtomicInteger calls=new AtomicInteger();
        @SubscribeEvent public static void observe(FixtureEvent event) { calls.incrementAndGet(); }
    }
    private static int checks;
    private static void check(boolean condition,String why) {
        checks++; if(!condition) throw new AssertionError(why);
    }
    public static void main(String[] arguments) throws Exception {
        String mode=arguments.length==0?"on":arguments[0];
        System.out.println("java="+System.getProperty("java.version")+" vendor="+System.getProperty("java.vendor")
                +" eventbusModule="+ASMEventHandler.class.getModule()+" mode="+mode);
        Field cache=ASMEventHandler.class.getDeclaredField("cache"); cache.setAccessible(true);
        Object original=cache.get(null);
        Method callback=Fixture.class.getMethod("observe",FixtureEvent.class);
        if(mode.equals("late")) new ASMEventHandler(Fixture.class,callback,false);
        List<String> info=new ArrayList<>(),warn=new ArrayList<>();
        var result=EventBusWrapperCacheFix.install(!mode.equals("off"),info::add,warn::add);
        System.out.println(result);
        check(result==EventBusWrapperCacheFix.install(true,info::add,warn::add),"installation must be one-shot");
        if(mode.equals("on")) {
            check(result.state()==EventBusWrapperCacheFix.State.APPLIED,"expected active replacement");
            check(cache.get(null)!=original,"cache changed");
            check(cache.get(null).getClass()==EventBusWrapperCacheFix.SynchronizedCache.class,"exact replacement class");
            check(info.size()==1 && warn.isEmpty(),"one success log");
        } else {
            check(result.state()==(mode.equals("off")?EventBusWrapperCacheFix.State.DISABLED:EventBusWrapperCacheFix.State.FALLBACK),"expected fallback/disabled");
            check(cache.get(null)==original && original.getClass()==HashMap.class,"fallback preserves original cache");
            check(warn.size()==(mode.equals("off")?0:1),"one warning or quiet disabled");
            if(!warn.isEmpty()) check(warn.get(0).contains(EventBusWrapperCacheFix.SETTING),"warning names setting");
        }
        ASMEventHandler handler=new ASMEventHandler(Fixture.class,callback,false);
        handler.invoke(new FixtureEvent());
        check(Fixture.calls.get()==1,"original wrapper dispatch");
        System.out.println("INFO="+info+" WARN="+warn);
        System.out.println("PASS checks="+checks);
    }
}
