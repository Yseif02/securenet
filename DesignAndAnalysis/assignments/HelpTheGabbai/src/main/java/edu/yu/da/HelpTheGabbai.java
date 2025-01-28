package edu.yu.da;

import java.util.*;

public class HelpTheGabbai extends HelpTheGabbaiBase{
    private final TreeMap<Integer, String> memberGrants;
    private final int totalGrants;

    /**
     * Constructor: client supplies at least one member and a corresponding
     * number of "amudGrants" such that the ith member is associated woth the ith
     * amudGrants element.
     *
     * @param members    at least one element.  It's the client's responsbility to
     *                   ensure that the parameter isn't null, that member names are not null, not
     *                   empty, and unique.
     * @param amudGrants number of times that the corresponding member will lead
     *                   the services.  It's the client's responsibiliity to ensure that all
     *                   elements have values greater than 0.
     */
    public HelpTheGabbai(String[] members, int[] amudGrants) {
        super(members, amudGrants);
        this.memberGrants = new TreeMap<>();

        int sum = 0;
        for (int i = 0; i < amudGrants.length; i++) {
            sum += amudGrants[i];
            memberGrants.put(sum, members[i]);
        }
        this.totalGrants = sum;
    }

    /**
     * Returns an iterator over the member ids.  The implementation of Iterator
     * MUST produce a stream of member ids that, over some reasonable period,
     * very closely reflects the number of each member's amudGrants.  More
     * proficient members will be selected more often to lead prayer services
     * than less proficient members; less proficient members will be selected to
     * the extent that their amudGrants entitles them.  As explained in the
     * requirements document, your implementation need not assign members their
     * exact number of assigned assigned amud grants: your algorithm will be
     * evaluated in terms of how "overall" all members leading the services often
     * enough with respected to their assigned amud grants.  The iterator is
     * exhausted when all amud grants have been used.
     * <p>
     * IMPORTANT: your implementation need not implement "remove()".
     * <p>
     * IMPORTANT: your implementation must use the default Iterator
     * implementation of "forEachRemaining()".
     */
    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
             final Random random = new Random();
             int grantsRemaining = totalGrants;


            @Override
            public boolean hasNext() {
                return grantsRemaining > 0;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int randomNum = random.nextInt(grantsRemaining) + 1;
                Map.Entry<Integer, String> entry = memberGrants.ceilingEntry(randomNum);
                String winner = entry.getValue();
                //reduceWights(entry);
                grantsRemaining--;
                return winner;
            }
        };
    }
}
