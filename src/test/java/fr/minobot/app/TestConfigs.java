package fr.minobot.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Builds a {@link Config} from the defaults with a few keys overridden — the same merge the loader
 * does, so a test states only the settings it actually cares about.
 */
public final class TestConfigs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestConfigs() {
    }

    /** @param overrides keys as they appear in {@code config.json}, e.g. {@code "window_cycle_order"} */
    public static Config with(Map<String, Object> overrides) {
        ObjectNode merged = MAPPER.valueToTree(Config.defaults());
        overrides.forEach((key, value) -> merged.set(key, MAPPER.valueToTree(value)));
        return MAPPER.convertValue(merged, Config.class);
    }

    /** The same, as the live configuration the core and the features actually take. */
    public static Settings settings(Map<String, Object> overrides) {
        return new Settings(with(overrides));
    }
}
