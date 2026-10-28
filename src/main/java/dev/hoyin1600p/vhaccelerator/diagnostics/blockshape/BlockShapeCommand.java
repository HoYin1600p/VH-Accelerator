package dev.hoyin1600p.vhaccelerator.diagnostics.blockshape;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.fml.loading.FMLPaths;

/** Explicit operator-only command; no automatic capture, configuration, or world writes. */
public final class BlockShapeCommand {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
    private BlockShapeCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("dump").requires(source -> source.hasPermission(4))
                .then(Commands.literal("blockshape").requires(source -> source.hasPermission(4))
                        .executes(context -> execute(context.getSource())));
    }

    public static int execute(CommandSourceStack source) {
        if (!source.hasPermission(4)) return 0;
        try {
            if (!source.getServer().isSameThread()) throw new IllegalStateException("Block shape capture requires the server thread");
            Instant now = Instant.now();
            var directory = FMLPaths.GAMEDIR.get().resolve("logs/vhaccelerator");
            Files.createDirectories(directory);
            var output = directory.resolve("blockshape-" + FILE_TIME.format(now) + "-" + UUID.randomUUID() + ".json.gz");
            var totals = BlockShapeDump.write(output, now, NativeBlockShapeAdapter.registry(), Function.identity());
            source.sendSuccess(new TextComponent("[VH Accelerator] Block shape snapshot: " + output.getFileName()
                    + " (" + totals.blockTypes() + " block types, " + totals.states() + " states)"), false);
            return 1;
        } catch (Exception | LinkageError | AssertionError failure) {
            VHAccelerator.LOGGER.warn("Block shape snapshot failed: {}", failure.toString());
            source.sendFailure(new TextComponent("[VH Accelerator] Block shape snapshot failed: " + failure.getClass().getSimpleName()));
            return 0;
        }
    }
}
