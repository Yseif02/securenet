package edu.yu.da;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public class WarGaming extends WarGamingBase {
    private final List<String> threats;
    //private Threat largestThreat;

    /**
     * Constructor.
     *
     * @param threats a non-empty immutable list of potential threats to be
     *                evaluated.  It's the client's responsibility to ensure that threat
     *                elements are not null, not empty, and unique with respect to one another.
     *                Ownership of this parameter remains with the client.
     */
    public WarGaming(List<String> threats) {
        super(threats);
        this.threats = threats;
        //this.largestThreat = new Threat(new ArrayList<>(), -1);
    }

    /**
     * Returns a "list of lists" in which each list element is a unique (sub)set
     * of threats to be evaluated, and the top-level list contains every possible
     * "is present"/"not present" combination of threats to be evaluated.  Every
     * micro-list element must be one of the "threats" parameter supplied to the
     * constructor, and may contain no duplicates.  The ORDER of micro-lists in
     * the global list is implementation dependent.  Each micro-list MUST be
     * REPRESENTED by the version of the micro-list that is in "canonical order"
     * (see design note above).  Thus if the threats parameter is ["a", "c", "b",
     * "d"], one micro-list that must be returned is ["a", "d"], and the
     * implementation MAY NOT return ["d", "a"] either as a replacement for ["a",
     * "d"] or as an addition.  In order words, a given instance of a threat
     * vector represents all possible orderings of its elements, but MUST BE
     * represented exactly once in canonical order.
     *
     * @return a list of lists representing all possible threat vectors.
     */
    @Override
    public List<List<String>> getThreatsToEvaluate() {
        List<List<String>> allSubsets = new ArrayList<>();
        Stack<ThreatSubset> stack = new Stack<>();

        stack.push(new ThreatSubset(0, new ArrayList<>()));

        while (!stack.isEmpty()) {
            ThreatSubset state = stack.pop();
            allSubsets.add(new ArrayList<>(state.currentSubset));

            for (int i = state.start; i < this.threats.size(); i++) {
                List<String> newSubset = new ArrayList<>(state.currentSubset);
                newSubset.add(this.threats.get(i));
                stack.push(new ThreatSubset(i + 1, newSubset));
            }
        }

        return allSubsets;
    }

    private static class ThreatSubset {
        int start;
        List<String> currentSubset;

        public ThreatSubset(int start, List<String> currentSubset) {
            this.start = start;
            this.currentSubset = currentSubset;
        }
    }



    /**
     * Returns the index in the getThreatsToEvaluate() list that corresponds to
     * the "unexpectedly dangerous" threat.  The unexpected threat is one whose
     * score (when evaluated by the Function parameter) is higher than it should
     * be when compared to the default score returned by every other threat
     * vector.
     *
     * @param f a Function that evaluates a threat vector and returns a score for
     *          the magnitude (larger score implies greater threat).  The default score
     *          returned for a threat vector is the number of threat elements in the
     *          vector.
     * @return int the index in the getThreatsToEvaluate() list that corresponds
     * to the "unexpectedly dangerous" threat.  If no element in the list is an
     * unexpected threat, return -1.
     */
    @Override
    public int identifyTheUnexpectedThreat(Function<List<String>, Integer> f) {
       /* boolean multipleHighLevelThreats = false;
        List<List<String>> subsets = getThreatsToEvaluate();
        Threat currentLargestThreat = new Threat(new ArrayList<>(), -1);
        int count = 0;
        for (List<String> subset : subsets) {
            Threat currentThreat = new Threat(subset, count++);
            int currentThreatDangerLevel = f.apply(currentThreat.getThreat());
            int currentHighestDangerLevel = f.apply(currentLargestThreat.getThreat());

            if (currentThreatDangerLevel > currentHighestDangerLevel) {
                currentLargestThreat = currentThreat;
                multipleHighLevelThreats = false;
            } else if (currentThreatDangerLevel == currentHighestDangerLevel) {
                multipleHighLevelThreats = true;
            }
        }
        return multipleHighLevelThreats ? -1 : currentLargestThreat.getIndex();
        return this.largestThreat.index;*/
        List<List<String>> allThreats = getThreatsToEvaluate();
        int currentIndex = 0;
        int indexOfLargestThreat = 0;
        int largestThreatDeviant = 0;
        for (List<String> threat : allThreats) {
            int expectedThreatSize = threat.size();
            int realThreatLevel = f.apply(threat);
            if (realThreatLevel > expectedThreatSize && realThreatLevel - expectedThreatSize > largestThreatDeviant) {
                largestThreatDeviant = realThreatLevel - expectedThreatSize;
                indexOfLargestThreat = currentIndex;
            }
            currentIndex++;
        }
        return (largestThreatDeviant == 0) ? -1 : indexOfLargestThreat;
    }

    /*private static class Threat extends ArrayList<String> implements Comparable<Threat> {
        int dangerLevel;
        int index;
        List<String> threat;
        boolean unexpectedDangerous;

        public Threat(List<String> threat, int index) {
            super(threat);
            this.dangerLevel = threat.size();
            this.index = index;
            this.threat = threat;
            this.unexpectedDangerous = false;
        }

        @Override
        public int compareTo(@NotNull Threat o) {
            return Integer.compare(this.dangerLevel, o.dangerLevel);
        }

        public int getIndex() {
            return this.index;
        }

        public List<String> getThreat() {
            return this.threat;
        }
    }*/
}
