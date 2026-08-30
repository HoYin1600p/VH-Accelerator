/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/entity/AttributeInstanceTemplates.java
 * Upstream commit: 5a93bc610973bf6631daf24dc077a629b2719726
 * Earlier upstream commit: 3926f27d33ad00f8ed738c6297fa1b4652e3067c
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; adapted the template interner to Forge 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.entity;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

public final class AttributeInstanceTemplates {
    private static final ObjectOpenCustomHashSet<AttributeInstance> INTERNER =
            new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
                @Override
                public int hashCode(AttributeInstance instance) {
                    if (instance == null) {
                        return 0;
                    }
                    int hash = System.identityHashCode(instance.getAttribute());
                    hash = 31 * hash + Double.hashCode(instance.getBaseValue());
                    hash = 31 * hash + instance.getModifiers().hashCode();
                    return hash;
                }

                @Override
                public boolean equals(
                        AttributeInstance first,
                        AttributeInstance second
                ) {
                    if (first == second) {
                        return true;
                    }
                    if (first == null || second == null) {
                        return false;
                    }
                    return first.getAttribute() == second.getAttribute()
                            && first.getBaseValue() == second.getBaseValue()
                            && first.getModifiers().equals(second.getModifiers());
                }
            });
    private static int requests;
    private static int uniqueTemplates;

    private AttributeInstanceTemplates() {
    }

    public static AttributeInstance intern(AttributeInstance instance) {
        if (instance == null || instance.getClass() != AttributeInstance.class) {
            return instance;
        }
        synchronized (INTERNER) {
            requests++;
            int sizeBefore = INTERNER.size();
            AttributeInstance canonical = INTERNER.addOrGet(instance);
            if (INTERNER.size() != sizeBefore) {
                uniqueTemplates++;
            }
            return canonical;
        }
    }

    public static Statistics statistics() {
        synchronized (INTERNER) {
            return new Statistics(requests, uniqueTemplates);
        }
    }

    public record Statistics(int requests, int uniqueTemplates) {
        public int reusedTemplates() {
            return requests - uniqueTemplates;
        }
    }
}
