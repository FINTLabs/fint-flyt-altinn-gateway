package no.fintlabs.golden;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares a result with a committed JSON file. Run with GOLDEN_UPDATE=true to rewrite the files.
 * The files lock the behaviour across library upgrades, so they must only change on purpose.
 */
final class GoldenFiles {

    private static final Path DIRECTORY = Path.of("src/test/resources/golden");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private GoldenFiles() {
    }

    static void assertMatchesGolden(String name, Object actual) throws IOException {
        String json = MAPPER.writeValueAsString(actual) + "\n";
        Path file = DIRECTORY.resolve(name + ".json");
        if (Boolean.parseBoolean(System.getenv("GOLDEN_UPDATE"))) {
            Files.createDirectories(DIRECTORY);
            Files.writeString(file, json);
            return;
        }
        assertEquals(Files.readString(file), json, "Result differs from " + file);
    }
}
