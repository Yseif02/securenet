package edu.yu.introtoalgs;

import java.util.*;

public class MultimediaConversion extends MultimediaConversionBase{
    private final Map<String, Map<String, Double>> mediaConversions;
    private final String sourceFormat;

    /**
     * Constructor: client passes the multimedia source format: the one that
     * needs to be converted to other formats
     *
     * @param sourceFormat the multimedia source format, can't be empty
     * @throws IllegalArgumentException as appropriate.
     */
    public MultimediaConversion(String sourceFormat) {
        super(sourceFormat);
        if (sourceFormat == null || sourceFormat.isEmpty()) {
            throw new IllegalArgumentException("sourceFormat can't be empty");
        }
        this.sourceFormat = sourceFormat;
        this.mediaConversions = new HashMap<>();

    }

    /**
     * Add a unit of multimedia conversion information: format1 can be converted
     * to format2 (and vice versa) with the process taking the specified
     * duration.  An exception must be thrown if the client attempts to add an
     * conversion specification that has previously been added (even if the
     * duration differs from the previous specification).
     *
     * @param format1  Name of the format1 multimedia format, can't be empty
     * @param format2  Name of the format2 multimedia format, can't be empty
     * @param duration the time (in ms) required to do the format conversion,
     *                 can't be negative.
     * @throws IllegalArgumentException as appropriate.
     */
    @Override
    public void add(String format1, String format2, double duration) {
        if (format1 == null || format1.isEmpty() || format2 == null || format2.isEmpty() || duration < 0 || format1.equals(format2)) throw new IllegalArgumentException();
        if (!mediaConversions.containsKey(format1)) this.mediaConversions.put(format1, new HashMap<>());
        if (!mediaConversions.containsKey(format2)) this.mediaConversions.put(format2, new HashMap<>());

        if (mediaConversions.get(format1).containsKey(format2)) throw new IllegalArgumentException("Conversion already exists");

        // add edges
        mediaConversions.get(format1).put(format2, duration);
        mediaConversions.get(format2).put(format1, duration);
    }

    /**
     * Convert the source format into as many as the specified output formats as
     * possible.  The rules for the conversion are specified in the requirements
     * document.
     *
     * @param outputFormats one or more output formats, each of which must have
     *                      been specified as a format in a previously invoked add() invocation.
     *                      The source format cannot be one of the specified output formats, nor can
     *                      the outputFormats contain duplicate formats.
     * @return a mapping of each of the specified output formats to the minimal
     * duration required to convert the source format to the output format.(See
     * the definition of "minimal duration" in the requirements document.) If the
     * source format cannot be converted to an output format, associate the
     * output format with Double.NaN.
     * <p>
     * NOTE: the conversion process can be done through one or more intemediary
     * conversion formats.
     * @throws IllegalArgumentException if the preconditions are violated.
     */
    @Override
    public Map<String, Double> convert(String... outputFormats) {
        if (outputFormats == null || outputFormats.length == 0 || Arrays.stream(outputFormats).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("outputFormats can't be empty");
        }

        Set<String> targets = new HashSet<>(List.of(outputFormats));
        if (targets.size() != outputFormats.length) {
            throw new IllegalArgumentException("output formats can't contain duplicates");
        }

        if (targets.contains(sourceFormat)) {
            throw new IllegalArgumentException("source format can't be one of the output formats");
        }

        for (String format : outputFormats) {
            if (!mediaConversions.containsKey(format)) {
                throw new IllegalArgumentException("output format not found");
            }
        }

        // Distance map to track the shortest duration to each format
        Map<String, Double> distances = new HashMap<>();
        // Map to track the minimum number of steps to each format
        Map<String, Integer> steps = new HashMap<>();
        // Priority queue: compare by steps first, then by distance
        PriorityQueue<FormatNode> pq = new PriorityQueue<>(
                Comparator.comparingInt(FormatNode::steps).thenComparingDouble(FormatNode::distance)
        );

        // Initialize distances and steps
        for (String format : mediaConversions.keySet()) {
            distances.put(format, Double.NaN);
            steps.put(format, Integer.MAX_VALUE);
        }
        distances.put(sourceFormat, 0.0);
        steps.put(sourceFormat, 0);
        pq.add(new FormatNode(sourceFormat, 0, 0.0));

        // Dijkstra-like algorithm with step tracking
        while (!pq.isEmpty()) {
            FormatNode current = pq.poll();
            String currentFormat = current.format();
            double currentDistance = current.distance();
            int currentSteps = current.steps();

            if (currentDistance > distances.get(currentFormat)) {
                continue; // Outdated entry
            }

            if (mediaConversions.containsKey(currentFormat)) {
                for (Map.Entry<String, Double> neighbor : mediaConversions.get(currentFormat).entrySet()) {
                    String neighborFormat = neighbor.getKey();
                    double neighborDistance = neighbor.getValue();
                    double newDistance = currentDistance + neighborDistance;
                    int newSteps = currentSteps + 1;

                    // Prioritize fewer steps, then distance
                    if (newSteps < steps.get(neighborFormat) ||
                            (newSteps == steps.get(neighborFormat) && newDistance < distances.get(neighborFormat))) {
                        steps.put(neighborFormat, newSteps);
                        distances.put(neighborFormat, newDistance);
                        pq.add(new FormatNode(neighborFormat, newSteps, newDistance));
                    }
                }
            }
        }

        // Prepare the result
        Map<String, Double> result = new HashMap<>();
        for (String target : targets) {
            result.put(target, distances.getOrDefault(target, Double.NaN));
        }
        return result;
    }

    // Helper class for priority queue
        private record FormatNode(String format, int steps, double distance) {
    }
}
