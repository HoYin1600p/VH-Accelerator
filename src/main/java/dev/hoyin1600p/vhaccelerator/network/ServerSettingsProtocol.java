package dev.hoyin1600p.vhaccelerator.network;

import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded primitive-only play packets. No client classes, arbitrary object deserialization or file paths. */
public final class ServerSettingsProtocol {
    public static final int MAX_KEYS = 256;
    public static final int MAX_KEY_LENGTH = 128;
    public record Request(long id, Map<String, Object> changes) {
        public Request { changes = Map.copyOf(changes); }
    }
    public record Reply(long id, ServerSettingsService.Result result) { }
    private ServerSettingsProtocol() { }

    public static void encodeRequest(Request request, FriendlyByteBuf buffer) {
        buffer.writeLong(request.id());
        buffer.writeVarInt(request.changes().size());
        request.changes().forEach((key, value) -> { buffer.writeUtf(key, MAX_KEY_LENGTH); writeValue(buffer, value); });
    }

    public static Request decodeRequest(FriendlyByteBuf buffer) {
        long id = buffer.readLong();
        int size = count(buffer);
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buffer.readUtf(MAX_KEY_LENGTH);
            if (changes.put(key, readValue(buffer)) != null) throw new IllegalArgumentException("Duplicate setting key");
        }
        return new Request(id, changes);
    }

    public static void encodeReply(Reply reply, FriendlyByteBuf buffer) {
        buffer.writeLong(reply.id());
        buffer.writeEnum(reply.result().status());
        buffer.writeVarInt(reply.result().values().size());
        reply.result().values().forEach((key, row) -> {
            buffer.writeUtf(key, MAX_KEY_LENGTH);
            writeValue(buffer, row.value()); writeValue(buffer, row.defaultValue());
            buffer.writeBoolean(row.min() != null && row.max() != null);
            if (row.min() != null && row.max() != null) {
                buffer.writeDouble(row.min().doubleValue()); buffer.writeDouble(row.max().doubleValue());
            }
            buffer.writeBoolean(row.restartRequired());
        });
        buffer.writeVarInt(reply.result().restartKeys().size());
        reply.result().restartKeys().forEach(key -> buffer.writeUtf(key, MAX_KEY_LENGTH));
    }

    public static Reply decodeReply(FriendlyByteBuf buffer) {
        long id = buffer.readLong();
        var status = buffer.readEnum(ServerSettingsService.Status.class);
        int size = count(buffer);
        Map<String, ServerSettingsService.Row> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buffer.readUtf(MAX_KEY_LENGTH);
            Object current = readValue(buffer), defaultValue = readValue(buffer);
            Double min = null, max = null;
            if (buffer.readBoolean()) { min = buffer.readDouble(); max = buffer.readDouble(); }
            var row = new ServerSettingsService.Row(current, defaultValue, min, max, buffer.readBoolean());
            if (values.put(key, row) != null) throw new IllegalArgumentException("Duplicate setting key");
        }
        int restarts = count(buffer);
        var keys = new java.util.ArrayList<String>();
        for (int i = 0; i < restarts; i++) keys.add(buffer.readUtf(MAX_KEY_LENGTH));
        if (status != ServerSettingsService.Status.OK && (!values.isEmpty() || !keys.isEmpty()))
            throw new IllegalArgumentException("Failed reply must not carry server values");
        return new Reply(id, new ServerSettingsService.Result(status, values, keys));
    }

    private static int count(FriendlyByteBuf buffer) {
        int value = buffer.readVarInt();
        if (value < 0 || value > MAX_KEYS) throw new IllegalArgumentException("Too many server settings");
        return value;
    }

    private static void writeValue(FriendlyByteBuf buffer, Object value) {
        if (value instanceof Boolean bool) { buffer.writeByte(0); buffer.writeBoolean(bool); }
        else if (value instanceof Integer integer) { buffer.writeByte(1); buffer.writeInt(integer); }
        else if (value instanceof Double number) { buffer.writeByte(2); buffer.writeDouble(number); }
        else throw new IllegalArgumentException("Unsupported server setting type");
    }

    private static Object readValue(FriendlyByteBuf buffer) {
        return switch (buffer.readUnsignedByte()) {
            case 0 -> buffer.readBoolean();
            case 1 -> buffer.readInt();
            case 2 -> buffer.readDouble();
            default -> throw new IllegalArgumentException("Unsupported server setting type");
        };
    }
}
