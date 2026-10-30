package com.pos.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseConnection {
    private static final Logger LOG = Logger.getLogger(DatabaseConnection.class.getName());
    private static final String FILE_NAME = "db.properties";
    private static final String ENV_FILE = ".env";
    private static final Map<String, String> ENV_KEYS = Map.of(
            "DB_URL", "db.url",
            "DB_USER", "db.user",
            "DB_PASSWORD", "db.password",
            "DB_POOL_SIZE", "db.pool.size");
    private static Properties props;
    // Supabase is reached over the internet, so connections are reused instead of opened per query.
    private static HikariDataSource pool;

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    private static synchronized HikariDataSource pool() throws SQLException {
        if (pool == null) {
            Properties p = properties();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(p.getProperty("db.url"));
            config.setUsername(p.getProperty("db.user"));
            config.setPassword(p.getProperty("db.password", ""));
            config.setMaximumPoolSize(Integer.parseInt(p.getProperty("db.pool.size", "5")));
            config.setMinimumIdle(1);
            config.setConnectionTimeout(15_000);
            // Fail on the first getConnection() instead of while building the pool, so the login screen can show the error.
            config.setInitializationFailTimeout(-1);
            config.setPoolName("pos-db");
            pool = new HikariDataSource(config);
        }

        return pool;
    }

    private static synchronized Properties properties() throws SQLException {
        if (props == null) {
            props = load();
            applyEnv(props);
            if (props.getProperty("db.url") == null) {
                throw new SQLException("DB_URL is not set. Copy .env.example to .env and fill in the Supabase connection.");
            }
        }

        return props;
    }

    // The credentials live in .env (kept out of git), not in db.properties.
    // Order: .env next to the installed jar, then .env in the working folder, then real environment variables.
    private static void applyEnv(Properties target) throws SQLException {
        Path folder = appFolder();
        Path envFile = folder == null ? null : folder.resolve(ENV_FILE);
        if (envFile == null || !Files.isRegularFile(envFile)) {
            envFile = Path.of(ENV_FILE).toAbsolutePath();
        }

        Map<String, String> values = new HashMap<>();
        if (Files.isRegularFile(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf('=');
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 1) {
                        continue;
                    }
                    String value = trimmed.substring(eq + 1).trim();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(trimmed.substring(0, eq).trim(), value);
                }
                LOG.info("Using database credentials from " + envFile);
            } catch (IOException e) {
                throw new SQLException("Could not read " + envFile, e);
            }
        }

        for (Map.Entry<String, String> key : ENV_KEYS.entrySet()) {
            String value = System.getenv(key.getKey());
            if (value == null) {
                value = values.get(key.getKey());
            }
            if (value != null && !value.isBlank()) {
                target.setProperty(key.getValue(), value);
            }
        }
    }

    private static Properties load() throws SQLException {
        Properties loaded = new Properties();
        Path folder = appFolder();
        Path external = folder == null ? null : folder.resolve("db.properties");
        if (external != null && Files.isRegularFile(external)) {
            try {
                Properties var13;
                try (InputStream in = Files.newInputStream(external)) {
                    loaded.load(in);
                    LOG.info("Using database settings from " + external);
                    var13 = loaded;
                }

                return var13;
            } catch (IOException var9) {
                throw new SQLException("Could not read " + external, var9);
            }
        } else {
            try {
                Properties var4;
                try (InputStream in = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
                    if (in == null) {
                        throw new SQLException("db.properties was found neither in the app folder nor on the classpath");
                    }

                    loaded.load(in);
                    LOG.info("Using the built-in db.properties");
                    var4 = loaded;
                }

                return var4;
            } catch (IOException var11) {
                throw new SQLException("Could not read the built-in db.properties", var11);
            }
        }
    }

    private static Path appFolder() {
        try {
            Path location = Path.of(DatabaseConnection.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (RuntimeException | URISyntaxException var1) {
            LOG.log(Level.FINE, "Could not determine the app folder", (Throwable)var1);
            return null;
        }
    }
}
