/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/textures/StbStitcher.java
 * Upstream commit: 94c848b0debbb5291ab3c709353e3f11613fd14d
 * Prior source: GTNewHorizons/lwjgl3ify StbStitcher.java at commit
 * f21364cd3d178aef863458a2faa1f5718a4e350d.
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors;
 * GTNewHorizons/lwjgl3ify contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted to VHA ownership and logging while retaining
 * the final 1.18-compatible STB binding selection and packing behavior.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.texture;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.lwjgl.stb.STBRPContext;
import org.lwjgl.stb.STBRPNode;
import org.lwjgl.stb.STBRPRect;
import org.lwjgl.stb.STBRectPack;

public final class StbTextureStitcher {
    private static final MethodHandle RECT_SHORT_SET;
    private static final MethodHandle RECT_INT_SET;
    private static final MethodHandle RECT_INT_X;
    private static final MethodHandle RECT_INT_Y;
    private static final MethodHandle RECT_SHORT_X;
    private static final MethodHandle RECT_SHORT_Y;

    static {
        MethodHandle shortMethod = null;
        MethodHandle intMethod = null;
        List<ReflectiveOperationException> exceptions = new ArrayList<>();
        try {
            intMethod = MethodHandles.publicLookup().findVirtual(
                    STBRPRect.class,
                    "set",
                    MethodType.methodType(
                            STBRPRect.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            boolean.class
                    )
            );
        } catch (ReflectiveOperationException exception) {
            exceptions.add(exception);
        }
        try {
            shortMethod = MethodHandles.publicLookup().findVirtual(
                    STBRPRect.class,
                    "set",
                    MethodType.methodType(
                            STBRPRect.class,
                            int.class,
                            short.class,
                            short.class,
                            short.class,
                            short.class,
                            boolean.class
                    )
            );
        } catch (ReflectiveOperationException exception) {
            exceptions.add(exception);
        }
        if (shortMethod == null && intMethod == null) {
            IllegalStateException failure = new IllegalStateException(
                    "An STBRPRect set method could not be located"
            );
            exceptions.forEach(failure::addSuppressed);
            throw failure;
        }
        RECT_SHORT_SET = shortMethod;
        RECT_INT_SET = intMethod;

        exceptions.clear();
        shortMethod = null;
        intMethod = null;
        try {
            intMethod = MethodHandles.publicLookup().findVirtual(
                    STBRPRect.class,
                    "x",
                    MethodType.methodType(int.class)
            );
        } catch (ReflectiveOperationException exception) {
            exceptions.add(exception);
        }
        try {
            shortMethod = MethodHandles.publicLookup().findVirtual(
                    STBRPRect.class,
                    "x",
                    MethodType.methodType(short.class)
            );
        } catch (ReflectiveOperationException exception) {
            exceptions.add(exception);
        }
        if (shortMethod == null && intMethod == null) {
            IllegalStateException failure = new IllegalStateException(
                    "An STBRPRect x method could not be located"
            );
            exceptions.forEach(failure::addSuppressed);
            throw failure;
        }
        RECT_SHORT_X = shortMethod;
        RECT_INT_X = intMethod;

        try {
            if (RECT_SHORT_X != null) {
                RECT_SHORT_Y = MethodHandles.publicLookup().findVirtual(
                        STBRPRect.class,
                        "y",
                        MethodType.methodType(short.class)
                );
                RECT_INT_Y = null;
            } else {
                RECT_INT_Y = MethodHandles.publicLookup().findVirtual(
                        STBRPRect.class,
                        "y",
                        MethodType.methodType(int.class)
                );
                RECT_SHORT_Y = null;
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "An STBRPRect y method could not be located",
                    exception
            );
        }
    }

    private StbTextureStitcher() {
    }

    public static Optional<
            Pair<Pair<Integer, Integer>, List<LoadableSpriteInfo>>
            > pack(
            Stitcher.Holder[] holders,
            int maxWidth,
            int maxHeight
    ) {
        int holderCount = holders.length;
        List<LoadableSpriteInfo> sprites = new ArrayList<>(holderCount);

        try (STBRPRect.Buffer rectangles = STBRPRect.malloc(holderCount);
             STBRPContext context = STBRPContext.malloc()) {
            long totalArea = 0L;
            int longestWidth = 0;
            int longestHeight = 0;
            for (int index = 0; index < holderCount; index++) {
                Stitcher.Holder holder = holders[index];
                int width = holder.width;
                int height = holder.height;
                set(
                        rectangles.get(index),
                        index,
                        width,
                        height,
                        0,
                        0,
                        false
                );
                totalArea += (long) width * height;
                longestWidth = Math.max(longestWidth, width);
                longestHeight = Math.max(longestHeight, height);
            }

            longestWidth = Mth.smallestEncompassingPowerOfTwo(longestWidth);
            longestHeight = Mth.smallestEncompassingPowerOfTwo(longestHeight);
            while (longestWidth * longestHeight < totalArea) {
                if (longestWidth <= longestHeight) {
                    longestWidth *= 2;
                } else {
                    longestHeight *= 2;
                }
            }

            int attempts = 0;
            while (true) {
                attempts++;
                if (longestWidth > maxWidth || longestHeight > maxHeight) {
                    return Optional.empty();
                }
                try (STBRPNode.Buffer nodes = STBRPNode.malloc(
                        longestWidth + 10
                )) {
                    STBRectPack.stbrp_init_target(
                            context,
                            longestWidth,
                            longestHeight,
                            nodes
                    );
                    STBRectPack.stbrp_pack_rects(context, rectangles);

                    for (STBRPRect rectangle : rectangles) {
                        if (!rectangle.was_packed()) {
                            Stitcher.Holder holder = holders[rectangle.id()];
                            throw stitchFailure(holder, holders);
                        }
                    }

                    for (STBRPRect rectangle : rectangles) {
                        Stitcher.Holder holder = holders[rectangle.id()];
                        sprites.add(new LoadableSpriteInfo(
                                holder.spriteInfo,
                                longestWidth,
                                longestHeight,
                                x(rectangle),
                                y(rectangle)
                        ));
                    }
                    return Optional.of(Pair.of(
                                Pair.of(longestWidth, longestHeight),
                                sprites
                    ));
                } catch (StitcherException exception) {
                    if (attempts >= 4) {
                        VHAccelerator.LOGGER.error(
                                "STB stitcher exhausted target atlas {}x{}",
                                longestWidth,
                                longestHeight
                        );
                        throw exception;
                    }
                    if (longestWidth <= longestHeight) {
                        longestWidth *= 2;
                    } else {
                        longestHeight *= 2;
                    }
                }
            }
        }
    }

    private static StitcherException stitchFailure(
            Stitcher.Holder holder,
            Stitcher.Holder[] holders
    ) {
        return new StitcherException(
                holder.spriteInfo,
                Stream.of(holders)
                        .map(candidate -> candidate.spriteInfo)
                        .collect(ImmutableList.toImmutableList())
        );
    }

    private static STBRPRect set(
            STBRPRect rectangle,
            int id,
            int width,
            int height,
            int x,
            int y,
            boolean packed
    ) {
        try {
            if (RECT_SHORT_SET != null) {
                return (STBRPRect) RECT_SHORT_SET.invokeExact(
                        rectangle,
                        id,
                        (short) width,
                        (short) height,
                        (short) x,
                        (short) y,
                        packed
                );
            }
            return (STBRPRect) RECT_INT_SET.invokeExact(
                    rectangle,
                    id,
                    width,
                    height,
                    x,
                    y,
                    packed
            );
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        }
    }

    private static int x(STBRPRect rectangle) {
        try {
            if (RECT_SHORT_X != null) {
                return (short) RECT_SHORT_X.invokeExact(rectangle);
            }
            return (int) RECT_INT_X.invokeExact(rectangle);
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        }
    }

    private static int y(STBRPRect rectangle) {
        try {
            if (RECT_SHORT_X != null) {
                return (short) RECT_SHORT_Y.invokeExact(rectangle);
            }
            return (int) RECT_INT_Y.invokeExact(rectangle);
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        }
    }

    public static final class LoadableSpriteInfo {
        private final TextureAtlasSprite.Info info;
        private final int atlasWidth;
        private final int atlasHeight;
        private final int x;
        private final int y;

        private LoadableSpriteInfo(
                TextureAtlasSprite.Info info,
                int atlasWidth,
                int atlasHeight,
                int x,
                int y
        ) {
            this.info = info;
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
            this.x = x;
            this.y = y;
        }

        public TextureAtlasSprite.Info info() {
            return this.info;
        }

        public int atlasWidth() {
            return this.atlasWidth;
        }

        public int atlasHeight() {
            return this.atlasHeight;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }
    }
}
