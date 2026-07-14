package fr.minobot.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

    public static Config load(Path configPath) {
        if (!Files.exists(configPath)) {
            log.info("No config file found. Creating {}", configPath);
            writeDefaults(configPath);
            return Config.defaults();
        }

        return readAndMerge(configPath).orElseGet(() -> {
            log.info("Falling back to default configuration.");
            return Config.defaults();
        });
    }

    /**
     * User values override defaults key by key; keys the user omitted keep their default. Without
     * this merge, a missing key would deserialize to 0/false rather than to its default.
     */
    private static Optional<Config> readAndMerge(Path configPath) {
        try {
            final var userConfig = MAPPER.readTree(Files.readString(configPath));
            if (!userConfig.isObject()) {
                log.warn("{} is not a JSON object. Using defaults.", configPath);
                return Optional.empty();
            }

            ObjectNode merged = MAPPER.valueToTree(Config.defaults());
            merged.setAll((ObjectNode) userConfig);

            final var config = MAPPER.treeToValue(merged, Config.class);
            log.info("Configuration loaded from {}", configPath);
            return Optional.of(config);
        } catch (IOException e) {
            log.error("Could not read the configuration: {}", e.getMessage());
            return Optional.empty();
        }
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
