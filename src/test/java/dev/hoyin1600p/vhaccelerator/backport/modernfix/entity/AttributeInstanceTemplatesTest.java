package dev.hoyin1600p.vhaccelerator.backport.modernfix.entity;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

final class AttributeInstanceTemplatesTest {
    @Test
    void canonicalizesEquivalentVanillaTemplates() {
        RangedAttribute attribute = new RangedAttribute(
                "attribute.test.vha.canonical",
                1.0,
                0.0,
                100.0
        );
        AttributeInstance first = instance(attribute, 12.0);
        AttributeInstance second = instance(attribute, 12.0);

        assertSame(first, AttributeInstanceTemplates.intern(first));
        assertSame(first, AttributeInstanceTemplates.intern(second));
    }

    @Test
    void preservesDifferentTemplatesAndDefensiveExclusions() {
        RangedAttribute attribute = new RangedAttribute(
                "attribute.test.vha.distinct",
                1.0,
                0.0,
                100.0
        );
        AttributeInstance first = instance(attribute, 10.0);
        AttributeInstance second = instance(attribute, 11.0);
        DerivedAttributeInstance derivedFirst =
                new DerivedAttributeInstance(attribute);
        DerivedAttributeInstance derivedSecond =
                new DerivedAttributeInstance(attribute);

        assertSame(first, AttributeInstanceTemplates.intern(first));
        assertSame(second, AttributeInstanceTemplates.intern(second));
        assertNotSame(
                AttributeInstanceTemplates.intern(derivedFirst),
                AttributeInstanceTemplates.intern(derivedSecond)
        );
        assertNull(AttributeInstanceTemplates.intern(null));
    }

    private static AttributeInstance instance(
            RangedAttribute attribute,
            double baseValue
    ) {
        AttributeInstance instance = new AttributeInstance(
                attribute,
                ignored -> {
                }
        );
        instance.setBaseValue(baseValue);
        return instance;
    }

    private static final class DerivedAttributeInstance
            extends AttributeInstance {
        private DerivedAttributeInstance(RangedAttribute attribute) {
            super(attribute, ignored -> {
            });
        }
    }
}
