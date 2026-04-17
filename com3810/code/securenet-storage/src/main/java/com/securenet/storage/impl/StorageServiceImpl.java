package com.securenet.storage.impl;

import com.securenet.common.JsonUtil;
import com.securenet.model.*;
import com.securenet.model.exception.DeviceNotFoundException;
import com.securenet.model.exception.VideoNotFoundException;
import com.securenet.storage.StorageService;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * PostgreSQL-backed implementation of the SecureNet Data Storage Layer.
 *
 * <p>All persistent data is stored in PostgreSQL and accessed via SQL/JDBC,
 * as specified in the Stage 2 C4 Container Diagram and Stage 3 design
 * document.
 *
 * <p>This class is wrapped by {@link com.securenet.storage.server.StorageServiceServer}
 * which exposes it over HTTP/JSON to all other SecureNet services.
 *
 * <h3>Data domains stored</h3>
 * <ul>
 *   <li>User data — homeowner accounts, hashed passwords, auth tokens</li>
 *   <li>Device state — device registry, status, firmware version</li>
 *   <li>Event history — time-ordered enriched SecurityEvent records</li>
 *   <li>Video archives — VideoClip metadata + raw video segment bytes</li>
 *   <li>Firmware — versioned firmware binaries for each DeviceType</li>
 *   <li>Push tokens — APNs/FCM token registry</li>
 * </ul>
 */
public class StorageServiceImpl implements StorageService {

    private final Connection connection;

    /**
     * Creates a new PostgreSQL-backed storage service and initializes the schema.
     *
     * @param jdbcUrl  JDBC connection URL, e.g.
     *                 {@code "jdbc:postgresql://localhost:5432/securenet"}
     * @param user     database username
     * @param password database password
     * @throws SQLException if the database connection or schema initialization fails
     */
    public StorageServiceImpl(String jdbcUrl, String user, String password) throws SQLException {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.connection = DriverManager.getConnection(jdbcUrl, user, password);
        initializeSchema();
        System.out.println("[StorageServiceImpl] Connected to PostgreSQL: " + jdbcUrl);
    }

    /**
     * Creates all tables and indexes if they do not already exist.
     * Schema is embedded directly to avoid classpath resource-loading issues.
     */
    private void initializeSchema() throws SQLException {
        String[] ddl = {
                // --- User data ---
                """
            CREATE TABLE IF NOT EXISTS users (
                user_id       VARCHAR(255) PRIMARY KEY,
                email         VARCHAR(255) NOT NULL UNIQUE,
                display_name  VARCHAR(255) NOT NULL,
                created_at    TIMESTAMP    NOT NULL,
                active        BOOLEAN      NOT NULL DEFAULT TRUE
            )""",

                """
            CREATE TABLE IF NOT EXISTS password_hashes (
                user_id       VARCHAR(255) PRIMARY KEY,
                password_hash VARCHAR(255) NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )""",

                """
            CREATE TABLE IF NOT EXISTS auth_tokens (
                token_value   VARCHAR(255) PRIMARY KEY,
                user_id       VARCHAR(255) NOT NULL,
                issued_at     TIMESTAMP    NOT NULL,
                expires_at    TIMESTAMP    NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )""",

                """
            CREATE TABLE IF NOT EXISTS revoked_tokens (
                token_value   VARCHAR(255) PRIMARY KEY,
                revoked_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""",

                // --- Device state ---
                """
            CREATE TABLE IF NOT EXISTS devices (
                device_id        VARCHAR(255) PRIMARY KEY,
                display_name     VARCHAR(255) NOT NULL,
                device_type      VARCHAR(50)  NOT NULL,
                owner_id         VARCHAR(255) NOT NULL,
                status           VARCHAR(50)  NOT NULL,
                registered_at    TIMESTAMP    NOT NULL,
                firmware_version VARCHAR(50)  NOT NULL,
                FOREIGN KEY (owner_id) REFERENCES users(user_id)
            )""",

                // --- Security event history ---
                """
            CREATE TABLE IF NOT EXISTS security_events (
                event_id    VARCHAR(255) PRIMARY KEY,
                device_id   VARCHAR(255) NOT NULL,
                owner_id    VARCHAR(255) NOT NULL,
                event_type  VARCHAR(50)  NOT NULL,
                occurred_at TIMESTAMP    NOT NULL,
                metadata    TEXT
            )""",

                "CREATE INDEX IF NOT EXISTS idx_events_device_time ON security_events(device_id, occurred_at)",
                "CREATE INDEX IF NOT EXISTS idx_events_owner_type  ON security_events(owner_id, event_type, occurred_at)",

                // --- Video archive ---
                """
            CREATE TABLE IF NOT EXISTS video_clips (
                clip_id         VARCHAR(255) PRIMARY KEY,
                device_id       VARCHAR(255) NOT NULL,
                owner_id        VARCHAR(255) NOT NULL,
                start_time      TIMESTAMP    NOT NULL,
                duration_millis BIGINT       NOT NULL,
                file_size_bytes BIGINT       NOT NULL,
                storage_key     VARCHAR(255) NOT NULL
            )""",

                """
            CREATE TABLE IF NOT EXISTS video_bytes (
                storage_key VARCHAR(255) PRIMARY KEY,
                raw_bytes   BYTEA NOT NULL
            )""",

                // --- Firmware binaries ---
                """
            CREATE TABLE IF NOT EXISTS firmware (
                device_type_key  VARCHAR(50)  NOT NULL,
                firmware_version VARCHAR(50)  NOT NULL,
                binary_data      BYTEA        NOT NULL,
                PRIMARY KEY (device_type_key, firmware_version)
            )""",

                """
            CREATE TABLE IF NOT EXISTS latest_firmware (
                device_type_key  VARCHAR(50)  PRIMARY KEY,
                firmware_version VARCHAR(50)  NOT NULL
            )""",

                // --- Push token registry ---
                """
            CREATE TABLE IF NOT EXISTS push_tokens (
                token     VARCHAR(255) PRIMARY KEY,
                user_id   VARCHAR(255) NOT NULL,
                platform  VARCHAR(10)  NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )""",

                "CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens(user_id)"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    // =====================================================================
    // User data
    // =====================================================================

    @Override
    public void saveUser(User user) throws IllegalArgumentException {
        Objects.requireNonNull(user, "user");
        String sql = "INSERT INTO users (user_id, email, display_name, created_at, active) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.userId());
            ps.setString(2, user.email());
            ps.setString(3, user.displayName());
            ps.setTimestamp(4, Timestamp.from(user.createdAt()));
            ps.setBoolean(5, user.active());
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("User or email already exists: " + user.email());
            }
            throw new RuntimeException("Failed to save user", e);
        }
    }

    @Override
    public Optional<User> findUserById(String userId) {
        String sql = "SELECT user_id, email, display_name, created_at, active FROM users WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapUser(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id", e);
        }
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        if (email == null) return Optional.empty();
        String sql = "SELECT user_id, email, display_name, created_at, active FROM users WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapUser(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by email", e);
        }
    }

    @Override
    public void updateUser(User user) throws IllegalArgumentException {
        Objects.requireNonNull(user, "user");
        String sql = "UPDATE users SET email = ?, display_name = ?, active = ? WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.email());
            ps.setString(2, user.displayName());
            ps.setBoolean(3, user.active());
            ps.setString(4, user.userId());
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalArgumentException("User does not exist: " + user.userId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update user", e);
        }
    }

    @Override
    public void savePasswordHash(String userId, String passwordHash) {
        String sql = "INSERT INTO password_hashes (user_id, password_hash) VALUES (?, ?) " +
                "ON CONFLICT (user_id) DO UPDATE SET password_hash = EXCLUDED.password_hash";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save password hash", e);
        }
    }

    @Override
    public Optional<String> findPasswordHashByUserId(String userId) {
        String sql = "SELECT password_hash FROM password_hashes WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(rs.getString("password_hash"));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find password hash", e);
        }
    }

    @Override
    public void saveAuthToken(AuthToken token) {
        String sql = "INSERT INTO auth_tokens (token_value, user_id, issued_at, expires_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, token.tokenValue());
            ps.setString(2, token.userId());
            ps.setTimestamp(3, Timestamp.from(token.issuedAt()));
            ps.setTimestamp(4, Timestamp.from(token.expiresAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save auth token", e);
        }
    }

    @Override
    public Optional<AuthToken> findAuthToken(String tokenValue) {
        String sql = "SELECT token_value, user_id, issued_at, expires_at FROM auth_tokens WHERE token_value = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenValue);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapAuthToken(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find auth token", e);
        }
    }

    @Override
    public void revokeAuthToken(String tokenValue) {
        String sql = "INSERT INTO revoked_tokens (token_value, revoked_at) VALUES (?, ?) " +
                "ON CONFLICT (token_value) DO NOTHING";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenValue);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke auth token", e);
        }
    }

    @Override
    public boolean isTokenRevoked(String tokenValue) {
        String sql = "SELECT 1 FROM revoked_tokens WHERE token_value = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenValue);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check token revocation", e);
        }
    }

    // =====================================================================
    // Device state
    // =====================================================================

    @Override
    public void saveDevice(Device device) throws IllegalArgumentException {
        Objects.requireNonNull(device, "device");
        String sql = "INSERT INTO devices (device_id, display_name, device_type, owner_id, status, registered_at, firmware_version) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, device.deviceId());
            ps.setString(2, device.displayName());
            ps.setString(3, device.type().name());
            ps.setString(4, device.ownerId());
            ps.setString(5, device.status().name());
            ps.setTimestamp(6, Timestamp.from(device.registeredAt()));
            ps.setString(7, device.firmwareVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Device already exists: " + device.deviceId());
            }
            throw new RuntimeException("Failed to save device", e);
        }
    }

    @Override
    public Optional<Device> findDeviceById(String deviceId) {
        String sql = "SELECT device_id, display_name, device_type, owner_id, status, registered_at, firmware_version FROM devices WHERE device_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapDevice(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find device", e);
        }
    }

    @Override
    public List<Device> findDevicesByOwner(String ownerId) {
        String sql = "SELECT device_id, display_name, device_type, owner_id, status, registered_at, firmware_version FROM devices WHERE owner_id = ? ORDER BY registered_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerId);
            ResultSet rs = ps.executeQuery();
            List<Device> devices = new ArrayList<>();
            while (rs.next()) devices.add(mapDevice(rs));
            return Collections.unmodifiableList(devices);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find devices by owner", e);
        }
    }

    @Override
    public void updateDevice(Device device) throws DeviceNotFoundException {
        Objects.requireNonNull(device, "device");
        String sql = "UPDATE devices SET display_name = ?, device_type = ?, owner_id = ?, status = ?, firmware_version = ? WHERE device_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, device.displayName());
            ps.setString(2, device.type().name());
            ps.setString(3, device.ownerId());
            ps.setString(4, device.status().name());
            ps.setString(5, device.firmwareVersion());
            ps.setString(6, device.deviceId());
            int rows = ps.executeUpdate();
            if (rows == 0) throw new DeviceNotFoundException(device.deviceId());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update device", e);
        }
    }

    @Override
    public void deleteDevice(String deviceId) throws DeviceNotFoundException {
        String sql = "DELETE FROM devices WHERE device_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new DeviceNotFoundException(deviceId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete device", e);
        }
    }

    // =====================================================================
    // Security event history
    // =====================================================================

    @Override
    public void saveEvent(SecurityEvent event) {
        Objects.requireNonNull(event, "event");
        String sql = "INSERT INTO security_events (event_id, device_id, owner_id, event_type, occurred_at, metadata) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.eventId());
            ps.setString(2, event.deviceId());
            ps.setString(3, event.ownerId());
            ps.setString(4, event.type().name());
            ps.setTimestamp(5, Timestamp.from(event.occurredAt()));
            ps.setString(6, JsonUtil.toJson(event.metadata()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save event", e);
        }
    }

    @Override
    public Optional<SecurityEvent> findEventById(String eventId) {
        String sql = "SELECT event_id, device_id, owner_id, event_type, occurred_at, metadata FROM security_events WHERE event_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapSecurityEvent(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find event", e);
        }
    }

    @Override
    public List<SecurityEvent> findEventsByDevice(String deviceId, Instant from, Instant to, int maxEvents) {
        String sql = "SELECT event_id, device_id, owner_id, event_type, occurred_at, metadata FROM security_events " +
                "WHERE device_id = ? AND occurred_at >= ? AND occurred_at <= ? ORDER BY occurred_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            ps.setInt(4, maxEvents);
            ResultSet rs = ps.executeQuery();
            List<SecurityEvent> events = new ArrayList<>();
            while (rs.next()) events.add(mapSecurityEvent(rs));
            return Collections.unmodifiableList(events);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find events by device", e);
        }
    }

    @Override
    public List<SecurityEvent> findEventsByOwnerAndType(String ownerId, String eventType,
                                                        Instant from, Instant to, int maxEvents) {
        String sql = "SELECT event_id, device_id, owner_id, event_type, occurred_at, metadata FROM security_events " +
                "WHERE owner_id = ? AND event_type = ? AND occurred_at >= ? AND occurred_at <= ? ORDER BY occurred_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ownerId);
            ps.setString(2, eventType);
            ps.setTimestamp(3, Timestamp.from(from));
            ps.setTimestamp(4, Timestamp.from(to));
            ps.setInt(5, maxEvents);
            ResultSet rs = ps.executeQuery();
            List<SecurityEvent> events = new ArrayList<>();
            while (rs.next()) events.add(mapSecurityEvent(rs));
            return Collections.unmodifiableList(events);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find events by owner and type", e);
        }
    }

    // =====================================================================
    // Video archive
    // =====================================================================

    @Override
    public void saveVideoClip(VideoClip clip, byte[] rawBytes) throws IllegalArgumentException {
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(rawBytes, "rawBytes");
        try {
            connection.setAutoCommit(false);

            String clipSql = "INSERT INTO video_clips (clip_id, device_id, owner_id, start_time, duration_millis, file_size_bytes, storage_key) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(clipSql)) {
                ps.setString(1, clip.clipId());
                ps.setString(2, clip.deviceId());
                ps.setString(3, clip.ownerId());
                ps.setTimestamp(4, Timestamp.from(clip.startTime()));
                ps.setLong(5, clip.duration().toMillis());
                ps.setLong(6, clip.fileSizeBytes());
                ps.setString(7, clip.storageKey());
                ps.executeUpdate();
            }

            String bytesSql = "INSERT INTO video_bytes (storage_key, raw_bytes) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(bytesSql)) {
                ps.setString(1, clip.storageKey());
                ps.setBytes(2, rawBytes);
                ps.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException re) { /* ignore */ }
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("Clip already exists: " + clip.clipId());
            }
            throw new RuntimeException("Failed to save video clip", e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { /* ignore */ }
        }
    }

    @Override
    public Optional<VideoClip> findClipById(String clipId) {
        String sql = "SELECT clip_id, device_id, owner_id, start_time, duration_millis, file_size_bytes, storage_key FROM video_clips WHERE clip_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, clipId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapVideoClip(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find clip", e);
        }
    }

    @Override
    public List<VideoClip> findClipsByDevice(String deviceId, Instant from, Instant to) {
        String sql = "SELECT clip_id, device_id, owner_id, start_time, duration_millis, file_size_bytes, storage_key FROM video_clips " +
                "WHERE device_id = ? AND start_time >= ? AND start_time <= ? ORDER BY start_time ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            ResultSet rs = ps.executeQuery();
            List<VideoClip> clips = new ArrayList<>();
            while (rs.next()) clips.add(mapVideoClip(rs));
            return Collections.unmodifiableList(clips);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find clips by device", e);
        }
    }

    @Override
    public byte[] loadVideoBytes(String storageKey) throws VideoNotFoundException {
        String sql = "SELECT raw_bytes FROM video_bytes WHERE storage_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes("raw_bytes");
            throw new VideoNotFoundException(storageKey);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load video bytes", e);
        }
    }

    // =====================================================================
    // Firmware binaries
    // =====================================================================

    @Override
    public String getLatestFirmwareVersion(String deviceTypeKey) {
        String sql = "SELECT firmware_version FROM latest_firmware WHERE device_type_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceTypeKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("firmware_version");
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get latest firmware version", e);
        }
    }

    @Override
    public byte[] loadFirmwareBinary(String deviceTypeKey, String firmwareVersion) throws IllegalArgumentException {
        String sql = "SELECT binary_data FROM firmware WHERE device_type_key = ? AND firmware_version = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, deviceTypeKey);
            ps.setString(2, firmwareVersion);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes("binary_data");
            throw new IllegalArgumentException("Firmware not found: " + deviceTypeKey + ":" + firmwareVersion);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load firmware binary", e);
        }
    }

    @Override
    public void saveFirmwareBinary(String deviceTypeKey, String firmwareVersion, byte[] binaryBytes) {
        try {
            connection.setAutoCommit(false);

            String fwSql = "INSERT INTO firmware (device_type_key, firmware_version, binary_data) VALUES (?, ?, ?) " +
                    "ON CONFLICT (device_type_key, firmware_version) DO UPDATE SET binary_data = EXCLUDED.binary_data";
            try (PreparedStatement ps = connection.prepareStatement(fwSql)) {
                ps.setString(1, deviceTypeKey);
                ps.setString(2, firmwareVersion);
                ps.setBytes(3, binaryBytes);
                ps.executeUpdate();
            }

            String latestSql = "INSERT INTO latest_firmware (device_type_key, firmware_version) VALUES (?, ?) " +
                    "ON CONFLICT (device_type_key) DO UPDATE SET firmware_version = EXCLUDED.firmware_version";
            try (PreparedStatement ps = connection.prepareStatement(latestSql)) {
                ps.setString(1, deviceTypeKey);
                ps.setString(2, firmwareVersion);
                ps.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException re) { /* ignore */ }
            throw new RuntimeException("Failed to save firmware binary", e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { /* ignore */ }
        }
    }

    // =====================================================================
    // Push token registry
    // =====================================================================

    @Override
    public void savePushToken(String userId, String token, String platform) {
        String sql = "INSERT INTO push_tokens (token, user_id, platform) VALUES (?, ?, ?) " +
                "ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, userId);
            ps.setString(3, platform);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save push token", e);
        }
    }

    @Override
    public void deletePushToken(String token) {
        String sql = "DELETE FROM push_tokens WHERE token = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete push token", e);
        }
    }

    @Override
    public List<String> findPushTokensByUser(String userId) {
        String sql = "SELECT token FROM push_tokens WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            List<String> tokens = new ArrayList<>();
            while (rs.next()) tokens.add(rs.getString("token"));
            return Collections.unmodifiableList(tokens);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find push tokens", e);
        }
    }

    // =====================================================================
    // ResultSet mappers
    // =====================================================================

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("user_id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("active")
        );
    }

    private static AuthToken mapAuthToken(ResultSet rs) throws SQLException {
        return new AuthToken(
                rs.getString("token_value"),
                rs.getString("user_id"),
                rs.getTimestamp("issued_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }

    private static Device mapDevice(ResultSet rs) throws SQLException {
        return new Device(
                rs.getString("device_id"),
                rs.getString("display_name"),
                DeviceType.valueOf(rs.getString("device_type")),
                rs.getString("owner_id"),
                DeviceStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("registered_at").toInstant(),
                rs.getString("firmware_version")
        );
    }

    private static SecurityEvent mapSecurityEvent(ResultSet rs) throws SQLException {
        String metadataJson = rs.getString("metadata");
        Map<String, String> metadata = (metadataJson != null && !metadataJson.isBlank())
                ? JsonUtil.gson().fromJson(metadataJson, new TypeToken<Map<String, String>>(){}.getType())
                : Map.of();

        return new SecurityEvent(
                rs.getString("event_id"),
                rs.getString("device_id"),
                rs.getString("owner_id"),
                EventType.valueOf(rs.getString("event_type")),
                rs.getTimestamp("occurred_at").toInstant(),
                metadata
        );
    }

    private static VideoClip mapVideoClip(ResultSet rs) throws SQLException {
        return new VideoClip(
                rs.getString("clip_id"),
                rs.getString("device_id"),
                rs.getString("owner_id"),
                rs.getTimestamp("start_time").toInstant(),
                Duration.ofMillis(rs.getLong("duration_millis")),
                rs.getLong("file_size_bytes"),
                rs.getString("storage_key")
        );
    }
}