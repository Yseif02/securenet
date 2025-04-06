package edu.yu.da;

import java.util.Arrays;
import java.util.PriorityQueue;

public class ExtendTheLightRail extends ExtendTheLightRailBase {
    int[] jLocations;
    int[] aLocations;
    TrainStop[] trainStops;
    int[] prepWork1;
    int[] prepWork2;
    int minPrepDuration;
    /**
     * Constructor.
     *
     * @param trainStops an array containing exactly n/2 jewish train stops and
     *                   exactly n/2 other ethnicity train stops.  Clients are responsible for
     *                   ensuring that the array isn't null, isn't empty, and contains exactly n/2
     *                   of each type.  Clients are responsible for ensuring that train stop
     *                   locations don't have negative-values, and are unique.
     */
    public ExtendTheLightRail(TrainStop[] trainStops) {
        super(trainStops);
        this.trainStops = trainStops;
        this.aLocations = new int[this.trainStops.length/2];
        this.jLocations = new int[this.trainStops.length/2];
        int jCount = 0;
        int aCount = 0;
        for (TrainStop trainStop : this.trainStops) {
            if (trainStop.isJewish) {jLocations[jCount++] = trainStop.location;}
            else {aLocations[aCount++] = trainStop.location;}
        }
        Arrays.sort(jLocations);
        Arrays.sort(aLocations);


        int[] prepCosts = new int[this.trainStops.length];
        for (int i = 0; i < this.trainStops.length; i++) {
            prepCosts[i] = this.trainStops[i].prepWorkCost;
        }
        this.prepWork1 = new int[this.trainStops.length/2];
        this.prepWork2 = new int[this.trainStops.length/2];
        Arrays.sort(prepCosts);
        int currentMinPrepDuration = 0;
        for (int i = 0; i < this.trainStops.length/2; i++) {
            this.prepWork1[i] = prepCosts[i];
            this.prepWork2[i] = prepCosts[this.trainStops.length - 1 - i];
            int pairCost = Math.max(this.prepWork1[i], this.prepWork2[i]);
            currentMinPrepDuration = Math.max(currentMinPrepDuration, pairCost);
        }
        this.minPrepDuration = currentMinPrepDuration;
    }

    /**
     * Returns the minimum possible cost of extending the light rail with n/2
     * unique extensions, subject to the constraint that every extension must
     * link train stops associated with different ethono-religious groups.
     */
    @Override
    public int cost() {
        int sum = 0;
        for (int i = 0; i < aLocations.length; i++) {
            sum += Math.abs(aLocations[i] - jLocations[i]);
        }
        return sum;
    }

    /**
     * After pairing the train stops, the ith element of the returned value
     * contains the location of a jewish train stop which is paired with the ith
     * element of the array returned by aLocations().
     *
     * @return array of size n/2 jewish train stop locations.
     */
    @Override
    public int[] jLocations() {
        return this.jLocations;
    }

    /**
     * After pairing the train stops, the ith element of the returned value
     * contains the location of the other ethnicity train stop which is paired
     * with the ith element of the array returned by jLocations().
     *
     * @return array of size n/2 other ethicity train stop locations.
     */
    @Override
    public int[] aLocations() {
        return this.aLocations;
    }

    /**
     * Returns the minimum possible duration time required to prepare ALL of the n
     * stations for extension work with work performed on n/2 pairs in parallel.
     */
    @Override
    public int minPreparationDuration() {
        return this.minPrepDuration;
    }

    /**
     * After pairing train stops such that the preparation work needed to do the
     * extensions will be performed as a unit, the ith element of prepWork1
     * stores the prepWorkCost of one train stop; the corresponding prepWorkCost of
     * the paired station is stored in the ith element of prepWork2.
     *
     * @return array of size n/2 containing one half of the preparation work pairs.
     */
    @Override
    public int[] prepWork1() {
        return this.prepWork1;
    }

    /**
     * After pairing train stops such that the preparation work needed to do the
     * extensions will be performed as a unit, the ith element of prepWork2
     * stores the prepWorkCost of one train stop; the corresponding prepWorkCost of
     * the paired station is stored in the ith element of prepWork1.
     *
     * @return array of size n/2 containing the other half of the preparation work pairs.
     */
    @Override
    public int[] prepWork2() {
        return this.prepWork2;
    }


}
