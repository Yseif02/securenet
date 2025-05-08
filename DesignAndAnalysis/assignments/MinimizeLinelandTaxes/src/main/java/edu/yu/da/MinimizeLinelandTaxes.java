package edu.yu.da;

import java.util.ArrayList;
import java.util.List;

public class MinimizeLinelandTaxes extends MinimizeLinelandTaxesBase {
    private final int numberOfPorts;
    private final int[] taxes;
    private static final int MAX_VALUE = Integer.MAX_VALUE / 2;


    private int bestCost;
    private List<TripSegment> bestRoute;

    /** Constructor.  Client supplies an array of 1..n tax requirements (the
     * array is of size 0..n, but the 0th element ISN'T USED) such that the ith
     * value specifies the tax requirement for use of the ith port, and ports are
     * labelled 1..n.
     *
     * When the constructor returns, the client must be able to invoke all
     * getters in O(1) cost: i.e., the constructor does all the work.
     *
     * @param taxes An array, of size at least 3, containing the tax requirements
     * for usage of a given port.  Values are non-negative-integer valued,
     * client's responsibility to ensure these semantics.  Client maintains
     * ownership.
     */
    public MinimizeLinelandTaxes(int[] taxes) {
        super(taxes);
        this.numberOfPorts = taxes.length - 1;
        this.taxes = taxes;
        runBellmanFord();
    }

    /** Returns the cost of the route that minimizes the tax fees incurred when
     * travelling from port 1 to port n.
     *
     * @return the minimum cost.  If no route is possible, returns -1.
     */
    @Override
    public int minTaxRouteCost() {
        return this.bestCost;
    }

    /** Returns the optimal route from port 1 to port n as a sequence of
     * TripSegments.  If no route is possible, returns an empty list.
     */
    @Override
    public List<TripSegment> minTaxRoute() {
        return this.bestRoute;
    }


    private void runBellmanFord() {
        int[][] dist = new int[this.numberOfPorts + 1][this.numberOfPorts + 1];
        int[][] prevPort = new int[this.numberOfPorts + 1][this.numberOfPorts + 1];
        int[][] prevJump = new int[this.numberOfPorts + 1][this.numberOfPorts + 1];

        for (int i = 1; i <= this.numberOfPorts; i++) {
            for (int j = 0; j <= this.numberOfPorts; j++) {
                dist[i][j] = MAX_VALUE;
                prevPort[i][j] = -1;
                prevJump[i][j] = -1;
            }
        }
        dist[1][0] = 0;

        int totalCells = (this.numberOfPorts + 1) * (this.numberOfPorts + 1);
        for (int i = 0; i < totalCells - 1; i++) {
            relaxEdges(dist, prevPort, prevJump);
        }


        int bestCost = MAX_VALUE;
        int bestJump = -1;
        for (int j = 0; j <= this.numberOfPorts; j++) {
            if (dist[this.numberOfPorts][j] < bestCost) {
                bestCost = dist[this.numberOfPorts][j];
                bestJump = j;
            }
        }

        List<TripSegment> route = new ArrayList<>();
        if (bestCost < MAX_VALUE) {
            int port = this.numberOfPorts;
            int jump = bestJump;
            while (port != 1 || jump != 0) {
                int from = prevPort[port][jump];
                int previousJumpLen = prevJump[port][jump];
                route.add(0, new TripSegment(from, port));
                port = from;
                jump = previousJumpLen;
            }
        } else {
            bestCost = -1;
        }

        this.bestCost = bestCost;
        this.bestRoute = route;
    }


    private void relaxEdges(int[][] dist, int[][] prevPort, int[][] prevJump) {
        for (int i = 1; i <= this.numberOfPorts; i++) {
            for (int j = 0; j <= this.numberOfPorts; j++) {
                int costSoFar = dist[i][j];
                if (costSoFar >= MAX_VALUE) continue;

                // try to move right
                int nextJumpRight = j + 1;
                int nextPortRight = i + nextJumpRight;
                if (nextJumpRight <= this.numberOfPorts && nextPortRight <= this.numberOfPorts) {
                    int newCost = costSoFar + this.taxes[nextPortRight];
                    if (newCost < dist[nextPortRight][nextJumpRight]) {
                        dist[nextPortRight][nextJumpRight] = newCost;
                        prevPort[nextPortRight][nextJumpRight] = i;
                        prevJump[nextPortRight][nextJumpRight] = j;
                    }
                }

                // move left
                if (j > 0) {
                    int previousPort = i - j;
                    if (previousPort >= 1) {
                        int newCost = costSoFar + this.taxes[previousPort];
                        if (newCost < dist[previousPort][j]) {
                            dist[previousPort][j] = newCost;
                            prevPort[previousPort][j] = i;
                            prevJump[previousPort][j] = j;
                        }
                    }
                }
            }
        }
    }
}