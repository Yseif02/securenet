package edu.yu.da;

import java.util.*;

public class MusicalChairs extends MusicalChairsBase {
    private final int nPeople;
    private final int nChairs;
    //private final int[] chairs;
    private final HashMap<Integer, ArrayList<Integer>> chairsForPersons;
    private final Set<Integer> people;
    private final UnionFind unionFind;
    /**
     * Constructor.
     *
     * @param nPeople number of people who will attempt to sit down:
     *                positive-integer valued.
     * @param nChairs number of chairs into which people will attempt to sit, no
     *                more than one persion per chair: positive-integer valued.
     * @throws IllegalArgumentException as appropriate.
     */
    public MusicalChairs(int nPeople, int nChairs) {
        super(nPeople, nChairs);
        this.nPeople = nPeople;
        this.nChairs = nChairs;
        if(nPeople < 1 || nChairs < 1) {
            throw new IllegalArgumentException();
        }
        //this.chairs = new int[nChairs + 1];
        this.chairsForPersons = new HashMap<>();
        this.people = new HashSet<>();
        this.unionFind = new UnionFind(nChairs);

        /*for(int i = 1; i <= nChairs; i++) {
            this.chairs[i] = -1;
        }*/
    }

    /**
     * Returns true iff personId who can sit only in either chair1 or chair2 is
     * able to sit, false otherwise.  The rules that determine whether the person
     * is able to sit are specified in the requirements doc.
     *
     * @param personId non-negative-integer valued, uniquely identifies the
     *                 person, must not have appeared previously in the sequence of "sitting
     *                 attempts".  Must be in the range 0..nPeople (inclusive, inclusive).
     *                 People need not try to sit in the order of their id.
     * @param chair1   positive-integer valued, a valid chair possibility for this
     *                 person, differs from chair2.  Must be in the range 1..nChairs (inclusive, exclusive).
     * @param chair2   positive-integer valued, an alternative chair possibility
     *                 for this person.  Must be in the range 1..nChairs (inclusive, exclusive).
     * @return true iff the person gets a chair given the previous sequence of
     * persons attempting to sit, false otherwise.
     * @throws IllegalArgumentException as appropriate
     */
    @Override
    public boolean tryToSit(int personId, int chair1, int chair2) {
        if (personId < 0 || personId >= this.nPeople || chair1 < 1 || chair1 >= this.unionFind.parent.length || chair2 < 1 || chair2 >= this.unionFind.parent.length) {
            throw new IllegalArgumentException("Invalid input");
        }
        if (this.people.contains(personId)) {
            throw new IllegalArgumentException("Person already sat");
        }

        this.chairsForPersons.put(personId, new ArrayList<>(List.of(chair1, chair2)));
        this.people.add(personId);

       int availableChair1 = this.unionFind.find(chair1);
       int availableChair2 = this.unionFind.find(chair2);

       if (!this.unionFind.isOccupied(availableChair1)) {
           this.unionFind.occupy(availableChair1);
           return true;
       }

       if (!this.unionFind.isOccupied(availableChair2)) {
           this.unionFind.occupy(availableChair2);
           return true;
       }

        int personToMove = this.unionFind.find(chair1);
        if (personToMove != -1 && canMoveOver(personToMove, chair1)) {
            this.unionFind.occupy(chair1);
            return true;
        }

        personToMove = this.unionFind.find(chair2);
        if (personToMove != -1 && canMoveOver(personToMove, chair2)) {
            this.unionFind.occupy(chair2);
            return true;
        }

        return false;
    }

    private boolean canMoveOver(int personToMove, int chair1) {
        ArrayList<Integer> chairs = this.chairsForPersons.get(personToMove);
        int chair2 = (chairs.get(0) == chair1) ? chairs.get(1) : chairs.get(0);

        int availableOtherChair = this.unionFind.find(chair2);
        if (!this.unionFind.isOccupied(availableOtherChair)) {
            this.unionFind.occupy(availableOtherChair);
            return true;
        }
        return false;
    }

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        public UnionFind(int size) {
            this.parent = new int[size + 1];
            this.rank = new int[size + 1];

            for(int i = 1; i <= size; i++) {
                this.parent[i] = i;
                this.rank[i] = 0;
            }
        }

        public int find(int chair) {
            if (chair == -1) {
                return -1;
            }
            if (this.parent[chair] != chair) {
                this.parent[chair] = find(this.parent[chair]);
            }
            return this.parent[chair];
        }

        /*public void union(int chair1, int chair2) {
            int chairRoot1 = find(chair1);
            int chairRoot2 = find(chair2);
            if(chairRoot1 != chairRoot2) {
                if (this.rank[chairRoot1] > this.rank[chairRoot2]) {
                    this.parent[chairRoot2] = chairRoot1;
                } else if (this.rank[chairRoot1] < this.rank[chairRoot2]) {
                    this.parent[chairRoot1] = chairRoot2;
                } else {
                    this.parent[chairRoot2] = chairRoot1;
                    this.rank[chairRoot1]++;
                }
            }
        }*/

        public void occupy(int chair) {
            this.parent[chair] = -1;
        }

        public boolean isOccupied (int chair) {
            return find(chair) == -1;
        }
    }
}
