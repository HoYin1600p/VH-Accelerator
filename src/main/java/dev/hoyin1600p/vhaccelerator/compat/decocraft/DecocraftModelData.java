package dev.hoyin1600p.vhaccelerator.compat.decocraft;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Trims what Decocraft 3.0.4 keeps of its parsed Blockbench models.
 *
 * <p><b>Shared registration parses.</b> Block registration parses a
 * {@code .bbmodel} from Decocraft's own jar once per block entry, so every
 * colour or material variant holds its own copy of the same file (about 4,000
 * parses of about 680 files). Everything that reads these models afterwards
 * (hit boxes, particles, seats, the animated renderer and its NBT) only reads
 * them, so entries with the same jar path now share one parse. The client
 * model loader, which reads through resource packs, is not affected.</p>
 *
 * <p><b>Unused texture data.</b> Drops the texture data Decocraft 3.0.4 parses from every {@code .bbmodel}
 * file and never reads again.
 *
 * <p>Each Blockbench file embeds its images as base64 PNG data URIs, plus the
 * author's local file paths. Decocraft parses a file once per block entry at
 * registration and keeps every result on its blocks and in
 * {@code ModuleBlocks.MAT_TO_BB_MODEL}; flag models embed every country's
 * image, so in Asgard this text was about 950 MB of the heap. Nothing in
 * Decocraft reads {@code BBModel.textures} after parsing: the bake uses the
 * block's {@code material} setting, and the renderer, hit boxes, particles and
 * NBT use elements, outliner and animations. The list and its entries stay in
 * place, only their image and path strings are cleared.</p>
 */
public final class DecocraftModelData {
    private static final String BB_MODEL = "com.razz.decocraft.models.bbmodel.BBModel";
    private static final String TEXTURE = "com.razz.decocraft.models.bbmodel.BBModelParts$Texture";
    private static final LongAdder STRIPPED_CHARS = new LongAdder();
    private static final LongAdder MODELS = new LongAdder();
    private static volatile Field textures;
    private static volatile Field[] cleared;
    private static volatile boolean failed;
    private static volatile Boolean enabled;
    private static final Map<String, Object> REGISTRATION_MODELS = new ConcurrentHashMap<>();
    private static final Map<String, Long> REGISTRATION_CRCS = new ConcurrentHashMap<>();
    private static final LongAdder CLIENT_REUSED = new LongAdder();
    private static final LongAdder SHARED = new LongAdder();

    private DecocraftModelData() {
    }

    /** The model already parsed from this jar path during registration, or null. */
    public static Object sharedRegistrationModel(String location) {
        if (location == null || !enabled()) {
            return null;
        }
        Object model = REGISTRATION_MODELS.get(location);
        if (model != null) {
            SHARED.increment();
        }
        return model;
    }

    /** {@code crc} is the jar entry's CRC-32, or -1 when unknown. */
    public static void rememberRegistrationModel(String location, Object model, long crc) {
        if (location != null && model != null && enabled()) {
            REGISTRATION_MODELS.putIfAbsent(location, model);
            if (crc >= 0L) {
                REGISTRATION_CRCS.putIfAbsent(location, crc);
            }
        }
    }

    /** Whether registration parsed this jar path with a known checksum. */
    public static boolean hasRegistrationChecksum(String jarPath) {
        return enabled() && REGISTRATION_CRCS.containsKey(jarPath);
    }

    /**
     * The registration parse of this jar path when the client resource has
     * exactly the same bytes (CRC-32 and length-independent content check by
     * checksum), so a resource-pack override is never replaced.
     */
    public static Object registrationModelMatching(String jarPath, long crc) {
        Long expected = REGISTRATION_CRCS.get(jarPath);
        if (expected == null || expected != crc) {
            return null;
        }
        Object model = REGISTRATION_MODELS.get(jarPath);
        if (model != null) {
            CLIENT_REUSED.increment();
        }
        return model;
    }

    /** Clears the unused image and path strings of a freshly parsed BBModel. */
    public static void strip(Object model) {
        if (model == null || failed || !enabled()) {
            return;
        }
        try {
            if (textures == null) {
                resolve(model.getClass());
            }
            if (!(textures.get(model) instanceof List<?> list)) {
                return;
            }
            long chars = 0L;
            for (Object texture : list) {
                if (texture == null || !TEXTURE.equals(texture.getClass().getName())) {
                    continue;
                }
                for (Field field : cleared) {
                    if (field.get(texture) instanceof String value) {
                        chars += value.length();
                        field.set(texture, null);
                    }
                }
            }
            MODELS.increment();
            STRIPPED_CHARS.add(chars);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failed = true;
            VHAccelerator.LOGGER.warn(
                    "Could not clear Decocraft's unused Blockbench texture data; keeping it",
                    failure
            );
        }
    }

    /** Logs how much was cleared, once model parsing has settled. */
    public static void report() {
        long models = MODELS.sum();
        if (models > 0L) {
            VHAccelerator.LOGGER.info(
                    "Decocraft Blockbench models: {} registration entries shared {} parsed files; "
                            + "client model loader reused {} of them; "
                            + "cleared unused texture data from {} parses ({} MB of text)",
                    SHARED.sum(),
                    REGISTRATION_MODELS.size(),
                    CLIENT_REUSED.sum(),
                    models,
                    STRIPPED_CHARS.sum() / (1024L * 1024L)
            );
        }
    }

    private static boolean enabled() {
        Boolean value = enabled;
        if (value == null) {
            value = VHAcceleratorConfig.decocraftModelTrimmingEnabled();
            enabled = value;
        }
        return value;
    }

    private static synchronized void resolve(Class<?> modelClass) throws ReflectiveOperationException {
        if (textures != null) {
            return;
        }
        if (!BB_MODEL.equals(modelClass.getName())) {
            throw new IllegalStateException("Unexpected Decocraft model class " + modelClass.getName());
        }
        Class<?> textureClass = Class.forName(TEXTURE, false, modelClass.getClassLoader());
        Field[] fields = {
                textureClass.getField("source"),
                textureClass.getField("path"),
                textureClass.getField("relative_path")
        };
        for (Field field : fields) {
            if (field.getType() != String.class) {
                throw new IllegalStateException("Unexpected Decocraft texture field " + field);
            }
        }
        cleared = fields;
        textures = modelClass.getField("textures");
    }
}
