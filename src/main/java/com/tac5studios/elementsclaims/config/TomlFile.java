package com.tac5studios.elementsclaims.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.tac5studios.elementsclaims.ElementsClaims;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written TOML file in config/elements_claims/.
 * The first time, the default file (with all its comments) is copied out of the mod jar.
 * After that the server owner's copy is read and never rewritten.
 * Keys missing from the owner's copy (added in a later version) fall back to the jar's default.
 */
public class TomlFile {

    private final String name;
    private Config data = Config.inMemory();
    private Config defaults = Config.inMemory();

    public TomlFile(String name) {
        this.name = name;
    }

    public Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(ElementsClaims.MOD_ID).resolve(name);
    }

    /** Copy the default if missing, then read the file. Returns false if it could not be read. */
    public boolean load() {
        defaults = readDefaults();
        Path p = path();
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                try (InputStream in = jarDefault()) {
                    if (in == null) throw new IllegalStateException("default " + name + " missing from the jar");
                    Files.copy(in, p);
                }
            }
            try (CommentedFileConfig cfg = CommentedFileConfig.builder(p).build()) {
                cfg.load();
                data = Config.copy(cfg);
            }
            return true;
        } catch (Exception e) {
            ElementsClaims.LOGGER.error("[Claims] Could not read {}. Using built-in defaults.", p, e);
            data = Config.inMemory();
            return false;
        }
    }

    private InputStream jarDefault() {
        return ElementsClaims.class.getResourceAsStream("/defaults/" + name);
    }

    private Config readDefaults() {
        try (InputStream in = jarDefault()) {
            if (in == null) return Config.inMemory();
            return new TomlParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ElementsClaims.LOGGER.error("[Claims] Built-in {} could not be read.", name, e);
            return Config.inMemory();
        }
    }

    private Object value(String key) {
        Object v = data.get(key);
        return v != null ? v : defaults.get(key);
    }

    public String str(String key, String def) {
        return value(key) instanceof String s ? s : def;
    }

    public boolean bool(String key, boolean def) {
        return value(key) instanceof Boolean b ? b : def;
    }

    public int num(String key, int def) {
        return value(key) instanceof Number n ? n.intValue() : def;
    }

    public long lng(String key, long def) {
        return value(key) instanceof Number n ? n.longValue() : def;
    }

    /** A list of plain strings. */
    public List<String> strings(String key) {
        List<String> out = new ArrayList<>();
        if (value(key) instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
        return out;
    }

    /** A list of inline tables, e.g. [{ item = "x", value = 5 }]. */
    public List<Config> tables(String key) {
        List<Config> out = new ArrayList<>();
        if (value(key) instanceof List<?> l) for (Object o : l) if (o instanceof Config c) out.add(c);
        return out;
    }
}
