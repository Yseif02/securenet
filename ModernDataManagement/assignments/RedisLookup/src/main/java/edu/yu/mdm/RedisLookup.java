package edu.yu.mdm;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RedisLookup extends RedisLookupBase {
    private static final String ACTIVE_NAMESPACE_KEY = "redislookup:active";
    private static final String LOCATION_KEY_PREFIX = ":location:";
    private static final String BLOCK_KEY_PREFIX = ":block:";
    private static final String EXISTS_FIELD = "_exists";
    private static final int PIPELINE_BATCH_SIZE = 10_000;

    public RedisLookup(Jedis conn, File blocks, File locations) {
        super(conn, blocks, locations);
        final String namespace = "redislookup:" + UUID.randomUUID();
        loadLocations(namespace, locations);
        loadBlocks(namespace, blocks);
        conn.set(ACTIVE_NAMESPACE_KEY, namespace);
    }

    public RedisLookup(Jedis conn) {
        super(conn);
    }

    private void loadLocations(final String namespace, final File locationsFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(locationsFile))) {
            String line = reader.readLine(); // skip header
            Pipeline pipeline = conn.pipelined();
            int pendingCommands = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                final String[] fields = parseCsvLine(line);
                if (fields.length < 6) {
                    continue;
                }

                final String locationId = safeValue(fields, 0);
                if (locationId == null || locationId.isBlank()) {
                    continue;
                }

                final String continentName = safeValue(fields, 3);
                final String countryName = safeValue(fields, 5);
                final String timeZone = safeValue(fields, 12);

                final Map<String, String> locationFields = new HashMap<>(4);
                locationFields.put(EXISTS_FIELD, "1");
                if (countryName != null) {
                    locationFields.put("country", countryName);
                }
                if (timeZone != null) {
                    locationFields.put("timezone", timeZone);
                }
                if (continentName != null) {
                    locationFields.put("continent", continentName);
                }

                pipeline.hset(locationKey(namespace, locationId), locationFields);
                pendingCommands++;

                if (pendingCommands >= PIPELINE_BATCH_SIZE) {
                    pipeline.sync();
                    pipeline = conn.pipelined();
                    pendingCommands = 0;
                }
            }

            pipeline.sync();
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load locations file: " + locationsFile, e);
        }
    }

    private void loadBlocks(final String namespace, final File blocksFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(blocksFile))) {
            String line = reader.readLine(); // skip header
            Pipeline pipeline = conn.pipelined();
            int pendingCommands = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                final String[] fields = parseBlockLine(line);
                if (fields.length < 2) {
                    continue;
                }

                final String network = safeValue(fields, 0);
                final String locationId = safeValue(fields, 1);

                if (network == null || locationId == null) {
                    continue;
                }

                final String baseIp = extractBaseIp(network);
                if (baseIp == null) {
                    continue;
                }

                pipeline.set(blockKey(namespace, baseIp), locationId);
                pendingCommands++;

                if (pendingCommands >= PIPELINE_BATCH_SIZE) {
                    pipeline.sync();
                    pipeline = conn.pipelined();
                    pendingCommands = 0;
                }
            }

            pipeline.sync();
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load blocks file: " + blocksFile, e);
        }
    }

    private String[] parseCsvLine(final String line) {
        return line.split(",(?=(?:[^\"]*\\\"[^\"]*\\\")*[^\"]*$)", -1);
    }

    private String[] parseBlockLine(final String line) {
        final int firstComma = line.indexOf(',');
        if (firstComma < 0) {
            return new String[] {line};
        }

        final int secondComma = line.indexOf(',', firstComma + 1);
        if (secondComma < 0) {
            return new String[] {line.substring(0, firstComma), line.substring(firstComma + 1)};
        }

        return new String[] {line.substring(0, firstComma), line.substring(firstComma + 1, secondComma)};
    }

    private String safeValue(final String[] fields, final int index) {
        if (index >= fields.length) {
            return null;
        }

        String value = fields[index];
        if (value == null) {
            return null;
        }

        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }

        return value.isEmpty() ? null : value;
    }

    private String extractBaseIp(final String network) {
        final int slashIndex = network.indexOf('/');
        if (slashIndex < 0) {
            return network;
        }
        return network.substring(0, slashIndex);
    }

    private String locationKey(final String namespace, final String locationId) {
        return namespace + LOCATION_KEY_PREFIX + locationId;
    }

    private String blockKey(final String namespace, final String ipAddress) {
        return namespace + BLOCK_KEY_PREFIX + ipAddress;
    }

    private long ipToLong(final String ip) {
        final String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }

        long result = 0;
        for (String part : parts) {
            final int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }
            result = result * 256 + octet;
        }
        return result;
    }

    /**
     * Given an ip address, attempts to map to location information.
     *
     * @param ipAddress IPv4 address represented in dotted-decimal notation as
     *                  four sets of numbers (0-255) separated by periods, such as 192.168.1.1.
     * @return null if mapping couldn't be performed (e.g., if the ip address or
     * location information not present in the supplied data-sets), otherwise an
     * array of size three: the first element corresponds to a "country name",
     * the second element corresponds to the "time zone", the third element
     * corresponds to the "continent name".  If any value for this three-element
     * aray is missing, supply null for that array elenment.
     */
    @Override
    public String[] ipAddressToLocation(final String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        try {
            ipToLong(ipAddress);
        }
        catch (RuntimeException e) {
            return null;
        }

        final String namespace = conn.get(ACTIVE_NAMESPACE_KEY);
        if (namespace == null) {
            return null;
        }

        final String locationId = conn.get(blockKey(namespace, ipAddress));
        if (locationId == null) {
            return null;
        }

        final List<String> location = conn.hmget(locationKey(namespace, locationId), EXISTS_FIELD, "country", "timezone", "continent");
        if (location.get(0) == null) {
            return null;
        }

        final String country = location.get(1);
        final String timezone = location.get(2);
        final String continent = location.get(3);

        return new String[] {country, timezone, continent};
    }
}
