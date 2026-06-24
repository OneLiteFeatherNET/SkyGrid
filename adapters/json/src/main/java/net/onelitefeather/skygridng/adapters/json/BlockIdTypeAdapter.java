package net.onelitefeather.skygridng.adapters.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.onelitefeather.skygridng.api.model.BlockId;

import java.io.IOException;

/**
 * Gson adapter that maps a {@link BlockId} to and from its canonical namespaced string (for example
 * {@code minecraft:stone}). Keeping blocks as strings in the JSON file means the configuration is
 * platform-neutral and human readable. A blank or missing value maps to {@code null} (used by the
 * optional {@code requiresGroundBlock}).
 */
final class BlockIdTypeAdapter extends TypeAdapter<BlockId> {

    @Override
    public void write(JsonWriter out, BlockId value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.asString());
        }
    }

    @Override
    public BlockId read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String raw = in.nextString();
        return raw == null || raw.isBlank() ? null : BlockId.of(raw);
    }
}
