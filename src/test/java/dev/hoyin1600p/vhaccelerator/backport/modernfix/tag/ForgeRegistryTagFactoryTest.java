package dev.hoyin1600p.vhaccelerator.backport.modernfix.tag;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraftforge.registries.tags.ITag;
import org.junit.jupiter.api.Test;

class ForgeRegistryTagFactoryTest {
    @Test
    void resolvesAndInvokesForge40PackagePrivateConstructor() {
        ITag<Object> tag = ForgeRegistryTagFactory.create(null);

        assertNotNull(tag);
        assertNull(tag.getKey());
    }
}
