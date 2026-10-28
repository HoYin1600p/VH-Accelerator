package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipResolver;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwner;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class EventBusWrapperCacheFixTest {
    public static final class FixtureEvent extends Event { }
    public static final class OtherEvent extends Event { }
    public static final class Fixture {
        static final AtomicInteger calls = new AtomicInteger();
        @SubscribeEvent public static void observe(FixtureEvent event) { calls.incrementAndGet(); }
        @SubscribeEvent public static void observe(OtherEvent event) { calls.incrementAndGet(); }
        @SubscribeEvent public void instance(FixtureEvent event) { calls.incrementAndGet(); }
    }

    private static Field field(Class<?> type,String name) throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    @SuppressWarnings("unchecked") private static Map<Method,Class<?>> cache(Class<?> type) throws Exception {
        return (Map<Method,Class<?>>)field(type,"cache").get(null);
    }
    private static EventBusWrapperCacheFix.Result install(Class<?> type) {
        return EventBusWrapperCacheFix.install(true,()->type,EventBusWrapperCacheFix::unsafeWriter);
    }

    @Test void disabledNeverLoadsEventBusOrUnsafe() {
        var result=EventBusWrapperCacheFix.install(false,()->{throw new AssertionError();},()->{throw new AssertionError();});
        assertEquals(EventBusWrapperCacheFix.State.DISABLED,result.state());
    }

    @Test void reviewedVersionAndBothWholeClassBodiesMustMatch() throws Exception {
        byte[] owner,loader;
        try (InputStream input=getClass().getResourceAsStream("/net/minecraftforge/eventbus/ASMEventHandler.class")) { owner=input.readAllBytes(); }
        try (InputStream input=getClass().getResourceAsStream("/net/minecraftforge/eventbus/ASMEventHandler$ASMClassLoader.class")) { loader=input.readAllBytes(); }
        assertNull(EventBusWrapperCacheFix.layoutProblem("5.0.3",owner,loader));
        assertNotNull(EventBusWrapperCacheFix.layoutProblem("5.0.7",owner,loader));
        assertNotNull(EventBusWrapperCacheFix.layoutProblem(null,owner,loader));
        byte[] changed=owner.clone(); changed[changed.length-1]^=1;
        assertNotNull(EventBusWrapperCacheFix.layoutProblem("5.0.3",changed,loader));
        changed=loader.clone(); changed[changed.length-1]^=1;
        assertNotNull(EventBusWrapperCacheFix.layoutProblem("5.0.3",owner,changed));
        assertNotNull(EventBusWrapperCacheFix.layoutProblem("5.0.3",null,loader));
    }

    @Test void installationIsEarlyAndPreservesOriginalWrapperCreationAndDispatch() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false);
        Object original=cache(type);
        assertEquals("5.0.3",EventBusWrapperCacheFix.version(type));
        assertEquals(EventBusWrapperCacheFix.State.APPLIED,install(type).state());
        assertNotSame(original,cache(type));
        assertInstanceOf(EventBusWrapperCacheFix.SynchronizedCache.class,cache(type));
        Method callback=Fixture.class.getMethod("observe",FixtureEvent.class);
        Object handler=EventBusWrapperCacheOracle.construct(type,Fixture.class,callback);
        int before=Fixture.calls.get();
        type.getMethod("invoke",Event.class).invoke(handler,new FixtureEvent());
        assertEquals(before+1,Fixture.calls.get());
        assertNotNull(cache(type).get(callback));
        assertEquals(1,((AtomicInteger)field(type,"IDs").get(null)).get());
    }

    @Test void refusesAUsedCacheOrChangedRuntimeMapWithoutTouchingIt() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false);
        Method callback=Fixture.class.getMethod("observe",FixtureEvent.class);
        EventBusWrapperCacheOracle.construct(type,Fixture.class,callback);
        Object old=cache(type);
        assertEquals(EventBusWrapperCacheFix.State.FALLBACK,install(type).state());
        assertSame(old,cache(type));
        type=EventBusWrapperCacheOracle.load(false);
        assertEquals(EventBusWrapperCacheFix.State.APPLIED,install(type).state());
        old=cache(type);
        assertEquals(EventBusWrapperCacheFix.State.FALLBACK,install(type).state());
        assertSame(old,cache(type));
    }

    @Test void missingDeniedOrThrowingUnsafeLeavesOriginalAndWarnsOnlyOnce() throws Exception {
        for (Throwable denial:List.of(new ClassNotFoundException("sun.misc.Unsafe"),
                new IllegalAccessException("denied"),new UnsupportedOperationException("future JVM"),new LinkageError("removed"))) {
            Class<?> type=EventBusWrapperCacheOracle.load(false);
            Object original=cache(type);
            var startup=new EventBusWrapperCacheFix.Installation();
            List<String> warnings=new ArrayList<>(); AtomicInteger attempts=new AtomicInteger();
            EventBusWrapperCacheFix.WriterFactory factory=()->{attempts.incrementAndGet(); throw denial;};
            var first=startup.apply(true,()->type,factory,message->fail(message),warnings::add);
            assertEquals(EventBusWrapperCacheFix.State.FALLBACK,first.state());
            assertSame(first,startup.apply(true,()->type,factory,message->fail(message),warnings::add));
            assertSame(original,cache(type)); assertEquals(1,attempts.get());
            assertEquals(1,warnings.size()); assertTrue(warnings.get(0).contains(EventBusWrapperCacheFix.SETTING));
        }
    }

    @Test void failedCompareAndSwapAndThrowingWriteLeaveOriginal() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false); Object original=cache(type);
        assertEquals(EventBusWrapperCacheFix.State.FALLBACK,
                EventBusWrapperCacheFix.install(true,()->type,()->(field,old,next)->false).state());
        assertSame(original,cache(type));
        assertEquals(EventBusWrapperCacheFix.State.FALLBACK,
                EventBusWrapperCacheFix.install(true,()->type,()->(field,old,next)->{throw new SecurityException("denied");}).state());
        assertSame(original,cache(type));
    }

    @Test void loggingFailureCannotUndoSuccessfulPublicationOrEscapeFallback() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false);
        var startup=new EventBusWrapperCacheFix.Installation();
        var result=startup.apply(true,()->type,EventBusWrapperCacheFix::unsafeWriter,
                message->{throw new LinkageError();},message->{throw new AssertionError();});
        assertEquals(EventBusWrapperCacheFix.State.APPLIED,result.state());
        assertInstanceOf(EventBusWrapperCacheFix.SynchronizedCache.class,cache(type));
        assertDoesNotThrow(()->EventBusWrapperCacheFix.log(
                new EventBusWrapperCacheFix.Result(EventBusWrapperCacheFix.State.FALLBACK,"denied"),
                message->{throw new AssertionError();},message->{throw new AssertionError();}));
    }

    @Test void synchronizedCacheKeepsNullAbsentAndReplacementBehavior() {
        var map=new EventBusWrapperCacheFix.SynchronizedCache();
        assertFalse(map.containsKey(null)); assertNull(map.get(null));
        assertNull(map.put(null,null)); assertTrue(map.containsKey(null)); assertNull(map.get(null));
        assertNull(map.put(null,String.class)); assertSame(String.class,map.get(null));
        assertSame(String.class,map.put(null,Integer.class));
        assertSame(Integer.class,map.get(null));
    }

    @Test void concurrentColdStaticAndInstanceWrappersHaveValidUniqueGeneratedClasses() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false); assertEquals(EventBusWrapperCacheFix.State.APPLIED,install(type).state());
        Method a=Fixture.class.getMethod("observe",FixtureEvent.class), b=Fixture.class.getMethod("instance",FixtureEvent.class);
        ExecutorService pool=Executors.newFixedThreadPool(8); CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for(int i=0;i<64;i++) { final boolean instance=i%2==1;
                futures.add(pool.submit(()->{start.await(); return EventBusWrapperCacheOracle.construct(type,instance?new Fixture():Fixture.class,instance?b:a);})); }
            start.countDown(); HashSet<Class<?>> classes=new HashSet<>(); int before=Fixture.calls.get();
            for (Future<Object> future:futures) {
                Object handler=future.get(5,TimeUnit.SECONDS);
                classes.add(field(type,"handler").get(handler).getClass());
                type.getMethod("invoke",Event.class).invoke(handler,new FixtureEvent());
            }
            assertEquals(before+64,Fixture.calls.get());
            assertEquals(2,cache(type).size());
            assertEquals(classes.size(),((AtomicInteger)field(type,"IDs").get(null)).get());
            assertEquals(classes.size(),classes.stream().map(Class::getName).distinct().count());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }

    @Test void collidingMethodHashesKeepDistinctOriginalWrappers() throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(false);
        assertEquals(EventBusWrapperCacheFix.State.APPLIED,install(type).state());
        Method a=Fixture.class.getMethod("observe",FixtureEvent.class), b=Fixture.class.getMethod("observe",OtherEvent.class);
        assertNotEquals(a,b); assertEquals(a.hashCode(),b.hashCode());
        Object first=EventBusWrapperCacheOracle.construct(type,Fixture.class,a);
        Object second=EventBusWrapperCacheOracle.construct(type,Fixture.class,b);
        assertNotSame(field(type,"handler").get(first).getClass(),field(type,"handler").get(second).getClass());
        assertSame(field(type,"handler").get(first).getClass(),cache(type).get(a));
        assertSame(field(type,"handler").get(second).getClass(),cache(type).get(b));
        int before=Fixture.calls.get();
        type.getMethod("invoke",Event.class).invoke(first,new FixtureEvent());
        type.getMethod("invoke",Event.class).invoke(second,new OtherEvent());
        assertEquals(before+2,Fixture.calls.get());
    }

    @Test void originalCacheReproducesNullWrapperAtRealHashMapResizePublication() throws Exception { race(false); }
    @Test void fixedCacheBlocksTheSameWindowThenReturnsOriginalWrapper() throws Exception { race(true); }

    private static void race(boolean fixed) throws Exception {
        Class<?> type=EventBusWrapperCacheOracle.load(true);
        if (fixed) assertEquals(EventBusWrapperCacheFix.State.APPLIED,install(type).state());
        Method callback=Fixture.class.getMethod("observe",FixtureEvent.class);
        Object warm=EventBusWrapperCacheOracle.construct(type,Fixture.class,callback);
        Map<Method,Class<?>> map=cache(type);
        Class<?> wrapper=field(type,"handler").get(warm).getClass();
        HashSet<String> names=new HashSet<>();
        for (Method key:String.class.getMethods()) { if (map.size()==12) break; if(names.add(key.getName())) map.put(key,wrapper); }
        assertEquals(12,map.size());
        Field table=field(HashMap.class,"table"); Object oldTable=table.get(map);
        assertEquals(16,java.lang.reflect.Array.getLength(oldTable));
        var pause=new EventBusWrapperCacheOracle.Pause();
        FutureTask<Object> reader=new FutureTask<>(()->EventBusWrapperCacheOracle.construct(type,Fixture.class,callback));
        Thread readerThread=new Thread(reader,"vha-cache-hit-reader"); readerThread.setDaemon(true); pause.reader=readerThread;
        Method newKey=Math.class.getMethod("abs",int.class);
        FutureTask<Object> writer=new FutureTask<>(()->map.put(newKey,wrapper));
        Thread writerThread=new Thread(writer,EventBusWrapperCacheOracle.WRITER); writerThread.setDaemon(true);
        try (var oracle=new EventBusWrapperCacheOracle()) {
            EventBusWrapperCacheOracle.pause=pause;
            readerThread.start(); assertTrue(pause.contains.await(5,TimeUnit.SECONDS));
            writerThread.start(); awaitState(writerThread,Thread.State.WAITING);
            Object newTable=table.get(map); assertNotSame(oldTable,newTable);
            assertEquals(32,java.lang.reflect.Array.getLength(newTable));
            for (Object entry:(Object[])newTable) assertNull(entry,"writer is paused before transferring the first bucket");
            pause.read.countDown();
            if (!fixed) {
                ExecutionException error=assertThrows(ExecutionException.class,()->reader.get(5,TimeUnit.SECONDS));
                assertInstanceOf(NullPointerException.class,error.getCause());
                assertTrue(error.getCause().getMessage().contains("createWrapper"));
            } else {
                awaitState(readerThread,Thread.State.BLOCKED); assertFalse(reader.isDone());
            }
            writerThread.setName("vha-cache-resize-release"); LockSupport.unpark(writerThread);
            writer.get(5,TimeUnit.SECONDS);
            if (fixed) assertSame(wrapper,field(type,"handler").get(reader.get(5,TimeUnit.SECONDS)).getClass());
            assertSame(wrapper,map.get(callback)); assertEquals(13,map.size());
        } finally {
            pause.read.countDown(); writerThread.setName("vha-cache-resize-release"); LockSupport.unpark(writerThread);
            readerThread.join(5000); writerThread.join(5000);
            assertFalse(readerThread.isAlive()); assertFalse(writerThread.isAlive());
        }
    }

    private static void awaitState(Thread thread,Thread.State expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(thread.getState()!=expected && thread.isAlive() && System.nanoTime()<deadline) Thread.sleep(1);
        assertEquals(expected,thread.getState());
    }

    @Test void pluginInstallsBeforeDiscoveryAndCorrectnessSettingSurvivesCompareMode() throws Exception {
        var feature=BackportFeature.EVENT_BUS_WRAPPER_CACHE_CONCURRENCY_FIX;
        assertTrue(feature.defaultEnabled()); assertTrue(feature.ignoresCompareMode());
        for(boolean client:new boolean[]{false,true}) {
            assertEquals(BackportOwner.VHA,BackportOwnershipResolver.resolve(feature,client,true,true,ModernFixOwnership.ABSENT).owner());
            assertEquals(BackportOwner.DISABLED,BackportOwnershipResolver.resolve(feature,client,false,false,ModernFixOwnership.ABSENT).owner());
        }
        ClassNode node=new ClassNode();
        try(InputStream input=getClass().getResourceAsStream("/dev/hoyin1600p/vhaccelerator/mixin/VHAcceleratorMixinPlugin.class")) { new ClassReader(input).accept(node,0); }
        var onLoad=node.methods.stream().filter(m->m.name.equals("onLoad")).findFirst().orElseThrow();
        int index=0,install=-1,discover=-1;
        for(var instruction:onLoad.instructions) { if(instruction instanceof MethodInsnNode call) {
            if(call.owner.endsWith("/EventBusWrapperCacheFix")&&call.name.equals("install")) install=index;
            if(call.owner.endsWith("/GateFactsDiscovery")&&call.name.equals("discover")) discover=index;
        } index++; }
        assertTrue(install>=0 && discover>install);
    }
}
