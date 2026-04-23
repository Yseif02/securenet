package edu.yu.mdm;

import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class RedisLookup extends RedisLookupBase {
    private static final String LOCATION_KEY_PREFIX = "location:";
    private static final String BLOCK_KEY_PREFIX = "block:";

    public RedisLookup(Jedis conn, File blocks, File locations) {
        super(conn, blocks, locations);
        conn.flushDB();
        loadLocations(locations);
        loadBlocks(blocks);
    }

    public RedisLookup(Jedis conn) {
        super(conn);
    }

    private void loadLocations(final File locationsFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(locationsFile))) {
            String line = reader.readLine(); // skip header
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

                final String redisKey = LOCATION_KEY_PREFIX + locationId;
                if (countryName != null) {
                    conn.hset(redisKey, "country", countryName);
                }
                if (timeZone != null) {
                    conn.hset(redisKey, "timezone", timeZone);
                }
                if (continentName != null) {
                    conn.hset(redisKey, "continent", continentName);
                }
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load locations file: " + locationsFile, e);
        }
    }

    private void loadBlocks(final File blocksFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(blocksFile))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                final String[] fields = parseCsvLine(line);
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

                final String blockKey = BLOCK_KEY_PREFIX + baseIp;
                conn.hset(blockKey, "locationId", locationId);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load blocks file: " + blocksFile, e);
        }
    }

    private String[] parseCsvLine(final String line) {
        return line.split(",(?=(?:[^\"]*\\\"[^\"]*\\\")*[^\"]*$)", -1);
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

        final String blockKey = BLOCK_KEY_PREFIX + ipAddress;
        final String locationId = conn.hget(blockKey, "locationId");
        if (locationId == null) {
            return null;
        }

        final String locationKey = LOCATION_KEY_PREFIX + locationId;
        final String country = conn.hget(locationKey, "country");
        final String timezone = conn.hget(locationKey, "timezone");
        final String continent = conn.hget(locationKey, "continent");

        if (country == null && timezone == null && continent == null) {
            return null;
        }

        return new String[] {country, timezone, continent};
    }
}