package fr.minobot.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code config.json}, falling back to {@link Config#defaults()} and writing that file out on
 * first run — same contract as {@code config_loader.py}.
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigLoader() {
    }

    /**
     * The directory the config lives in: next to the executable when packaged, the project root
     * otherwise. Transposes the {@code sys.frozen} branch of {@code app.py}.
     */
    public static Path baseDirectory() {
        return ProcessHandle.current().info().command()
                .map(Path::of)
                .filter(command -> !command.getFileName().toString().startsWith("java"))
                .map(Path::getParent)
                .orElseGet(() -> Path.of("").toAbsolutePath());
    }

    /** The defaults merged with {@code config.json} alone — no persisted overlay state. */
    public static Config load(Path configPath) {
        return load(configPath, null);
    }

    /**
     * Builds the live configuration from its two tiers: {@code config.json}, the read-only defaults,
     * then {@code overlay.json}, the overlay's persisted overrides laid on top. Either may be absent.
     */
    public static Config load(Path configPath, Path overlayPath) {
        if (!Files.exists(configPath)) {
            log.info("No config file found. Creating {}", configPath);
            writeDefaults(configPath);
        }

        final var base = baseTree(configPath);
        if (overlayPath != null) {
            OverlayState.overlay(base, overlayPath);
        }

        try {
            return MAPPER.treeToValue(base, Config.class);
        } catch (IOException e) {
            log.error("Could not build the configuration: {}", e.getMessage());
            return Config.defaults();
        }
    }

    /**
     * The defaults with {@code config.json} laid over them, key by key: keys the user omitted keep
     * their default, so a missing key never deserializes to 0/false. A missing or malformed file
     * leaves the defaults alone.
     */
    private static ObjectNode baseTree(Path configPath) {
        final ObjectNode defaults = MAPPER.valueToTree(Config.defaults());
        if (!Files.exists(configPath)) {
            return defaults;
        }

        try {
            final var userConfig = MAPPER.readTree(Files.readString(configPath));
            if (!userConfig.isObject()) {
                log.warn("{} is not a JSON object. Using defaults.", configPath);
                return defaults;
            }
            defaults.setAll((ObjectNode) userConfig);
            log.info("Configuration loaded from {}", configPath);
        } catch (IOException e) {
            log.error("Could not read the configuration: {}", e.getMessage());
        }
        return defaults;
    }

    private static void writeDefaults(Path configPath) {
        try {
            final var parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), Config.defaults());
            log.info("{} generated.", configPath);
        } catch (IOException e) {
            log.error("Could not create the configuration file: {}", e.getMessage());
        }
    }
}
