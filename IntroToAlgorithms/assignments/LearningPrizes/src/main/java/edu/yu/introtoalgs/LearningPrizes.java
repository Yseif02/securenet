package edu.yu.introtoalgs;

import java.util.*;

public class LearningPrizes extends LearningPrizesBase{
    private double prizeWeightingConstant;
    private HashMap<Integer, Participant> participants;
    private HashMap<Integer, TreeSet<Ticket>> dailyTickets;
    private LinkedList<Integer> daysWithTickets;
    private TreeSet<Ticket> ticketPool;
    private int currentDay;
    private int lastDayAwardedPrize;
    private int firstDay;


    /**
     * Constructor: supplies the prize weighting constant, and initializes state
     * to no tickets added to the learning program.
     *
     * @param prizeWeightingConstant (a positive value) when used to calculate
     *                               awarded prize money, weights the result of the ("max - min") computation
     *                               to determine the amount that's actually awarded
     * @throws IllegalArgumentException if any of the pre-conditions are violated.
     */
    public LearningPrizes(double prizeWeightingConstant) {
        super(prizeWeightingConstant);
        this.prizeWeightingConstant = prizeWeightingConstant;
        this.participants = new HashMap<>();
        this.dailyTickets = new HashMap<>();
        this.daysWithTickets = new LinkedList<>();
        this.ticketPool = new TreeSet<>();
        this.currentDay = 0;
        this.lastDayAwardedPrize = 0;
        this.firstDay = 0;
    }

    /**
     * Adds a ticket to the learning program.  A given child can add a ticket a
     * maximum of one time per day.
     *
     * @param day          positive integer, that identifies the day in which the child
     *                     learned the specified number of hours.  Days need not be sequential (e.g.,
     *                     no tickets may have been submitted that day), but can never decrease in
     *                     value from ANY tickets submitted previously.  Whenever a ticket specifies
     *                     a day value greater than previously seen, this signifies a "new learning
     *                     program day": subsequent tickets that specify an earlier day are invalid
     *                     and must be rejected by the system.
     *                     <p>
     *                     If fewer than two tickets were submitted on a given day, no prize money is
     *                     awarded for that day's learning, and the tickets remain in the pool
     *                     (potentially being awarded prizes on subsequent days)
     * @param childId      non-negative integer, uniquely identifies the child.
     * @param hoursLearned positive-value, the number of hours learned on that
     *                     day by this child
     * @throws IllegalArgumentException if any of the pre-conditions are
     *                                  violated.
     */
    @Override
    public void addTicket(int day, int childId, double hoursLearned) {
        if (day < 1 || hoursLearned <= 0 || childId < 0 || this.lastDayAwardedPrize >= day) throw new IllegalArgumentException();
        Participant participant;
        if (this.participants.containsKey(childId)) {
            participant = this.participants.get(childId);
            if (day <= participant.dayOfLastTicketAdded || day < this.daysWithTickets.getLast()){
                return;
            }
            addTicketForParticipant(day, childId, hoursLearned, participant);
        } else {
            participant = new Participant(childId, day);
            this.participants.put(childId, participant);
            addTicketForParticipant(day,childId, hoursLearned, participant);
        }
        if (this.firstDay == 0) {
            this.firstDay = day;
        }
    }

    private void addTicketForParticipant(int day, int childId, double hoursLearned, Participant participant) {
        participant.dayOfLastTicketAdded = day;
        //first ticket of current day
        if (this.dailyTickets.containsKey(day)) {
            TreeSet<Ticket> ticketsOfCurrentDay = this.dailyTickets.get(day);
            Ticket ticket = new Ticket(childId, day, hoursLearned);
            ticketsOfCurrentDay.add(ticket);
            //this.dailyTickets.get(day).add(new Ticket(childId, day, hoursLearned));
        } else {

            TreeSet<Ticket> ticketsOfCurrentDay = new TreeSet<>();
            this.dailyTickets.put(day, ticketsOfCurrentDay);
            ticketsOfCurrentDay.add(new Ticket(childId, day, hoursLearned));
            this.currentDay = day;
            this.daysWithTickets.add(day);
        }
    }

    /**
     * An iterator returning the prize money awarded on successive days (from
     * the first day through the last day that tickets were processed).  Your
     * implementation is allowed to throw UnsupportedOperationException()
     * if the client invokes either forEachRemaining() or remove().
     * <p>
     * IMPORTANT: by invoking this method, the client implicitly instructs the
     * implementation to compute prize money for the current day, followed by
     * "bumping the day counter" to end processing of tickets for the current day
     * and starting a new processing period.  Tickets that arrive after this
     * method is invoked must therefore be for a day that's later than the
     * current day.
     * <p>
     * IMPLICATION: given a large pool of submitted tickets, the learning program
     * will calculate N prize money awards if clients invoke the iterator N times
     * on the existing pool of tickets (even if e.g., all tickets are for day
     * #1).  After those N invocations, the N days of prize money will be
     * available via N invocations of iterator.next()
     *
     * @return Iterator over the sequence of awarded prize money.
     * @see #addTicket
     */
    @Override
    public Iterator<Double> awardedPrizeMoney() {
        List<Double> awardedPrizeMoney = new ArrayList<>();
        //pre-calculate prize money for each day that has greater than 2 tickets
        //in the pool and store it in a list. Then return an iterator for that list.
        //If a day has less than 2 tickets, skip it and move to the next day.
        for (int day = this.lastDayAwardedPrize + 1; day <= this.currentDay; day++) {
            if (dailyTickets.containsKey(day)) {
                this.ticketPool.addAll(this.dailyTickets.get(day));
            }
            if (this.ticketPool.size() >= 2) {
                Ticket minTicket = this.ticketPool.first();
                Ticket maxTicket = this.ticketPool.last();
                double prizeMoney = (maxTicket.hoursLearned() - minTicket.hoursLearned()) * this.prizeWeightingConstant;
                awardedPrizeMoney.add(prizeMoney);
                this.ticketPool.remove(minTicket);
                this.ticketPool.remove(maxTicket);
                this.lastDayAwardedPrize = day;
            }/* else {
                awardedPrizeMoney.add(0.0);
                this.lastDayAwardedPrize = day;
            }*/
        }

        return awardedPrizeMoney.iterator();

        /*return new Iterator<Double>() {
            private int currentDay = lastDayAwardedPrize + 1;
            private final TreeSet<Ticket> ticketPool = new TreeSet<>();

            @Override
            public boolean hasNext() {
                return currentDay <= LearningPrizes.this.currentDay;
            }

            @Override
            public Double next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                // Add tickets for the current day to the pool
                for (int day = lastDayAwardedPrize + 1; day <= currentDay; day++) {
                    if (dailyTickets.containsKey(day)) {
                        ticketPool.addAll(dailyTickets.get(day));
                    }
                }

                // Skip days with less than 2 tickets
                while (ticketPool.size() < 2 && currentDay <= LearningPrizes.this.currentDay) {
                    currentDay++;
                    for (int day = lastDayAwardedPrize + 1; day <= currentDay; day++) {
                        if (dailyTickets.containsKey(day)) {
                            ticketPool.addAll(dailyTickets.get(day));
                        }
                    }
                }

                if (ticketPool.size() < 2) {
                    throw new NoSuchElementException();
                }

                Ticket minTicket = ticketPool.first();
                Ticket maxTicket = ticketPool.last();
                double prize = (maxTicket.hoursLearned() - minTicket.hoursLearned()) * prizeWeightingConstant;

                // Remove the min and max tickets from the pool
                ticketPool.remove(minTicket);
                ticketPool.remove(maxTicket);

                lastDayAwardedPrize = currentDay;
                currentDay++;
                return prize;
            }
        };*/
    }



    private static class Participant{
        private final int id;
        private int dayOfLastTicketAdded;

        private Participant(int id, int day){
            this.id = id;
            this.dayOfLastTicketAdded = day;
        }


        public int getId() {
            return id;
        }

        public int getDayOfLastTicketAdded() {
            return dayOfLastTicketAdded;
        }

        public void setDayOfLastTicketAdded(int dayOfLastTicketAdded) {
            this.dayOfLastTicketAdded = dayOfLastTicketAdded;
        }
    }

    private record Ticket(int participantId, int day, double hoursLearned) implements Comparable<Ticket> {

    /**
         * Compares this object with the specified object for order.  Returns a
         * negative integer, zero, or a positive integer as this object is less
         * than, equal to, or greater than the specified object.
         *
         * <p>The implementor must ensure {@link Integer#signum
         * signum}{@code (x.compareTo(y)) == -signum(y.compareTo(x))} for
         * all {@code x} and {@code y}.  (This implies that {@code
         * x.compareTo(y)} must throw an exception if and only if {@code
         * y.compareTo(x)} throws an exception.)
         *
         * <p>The implementor must also ensure that the relation is transitive:
         * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies
         * {@code x.compareTo(z) > 0}.
         *
         * <p>Finally, the implementor must ensure that {@code
         * x.compareTo(y)==0} implies that {@code signum(x.compareTo(z))
         * == signum(y.compareTo(z))}, for all {@code z}.
         *
         * @param o the object to be compared.
         * @return a negative integer, zero, or a positive integer as this object
         * is less than, equal to, or greater than the specified object.
         * @throws NullPointerException if the specified object is null
         * @throws ClassCastException   if the specified object's type prevents it
         *                              from being compared to this object.
         * @apiNote It is strongly recommended, but <i>not</i> strictly required that
         * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any
         * class that implements the {@code Comparable} interface and violates
         * this condition should clearly indicate this fact.  The recommended
         * language is "Note: this class has a natural ordering that is
         * inconsistent with equals."
         */
        @Override
        public int compareTo(Ticket o) {
            //compare by hoursLearned
            int hoursComparison = Double.compare(this.hoursLearned, o.hoursLearned);
            if (hoursComparison != 0) {
                return hoursComparison;
            }

            // if hoursLearned are the same, compare by participantId
            int idComparison = Integer.compare(this.participantId, o.participantId);
            if (idComparison != 0) {
                return idComparison;
            }

            // if participantId is the same, compare by day
            return Integer.compare(this.day, o.day);
        }
    }


}
