package edu.yu.da;

public class MaxTheKiddush extends MaxTheKiddushBase {
    int[] bookings;
    int[] prefixSumArray;
    int maxCapacity;
    boolean canBook;
    boolean isUniform;
    /**
     * Constructor.
     *
     * @param bookings    an ordered sequence of the number of tables required to be
     *                    booked by each member for a given kiddush.  May not be null or empty,
     *                    elements must be positive-integer-valued.
     * @param maxCapacity a positive integer representing the maximum number of
     *                    tables available in the kiddush room.
     * @throws IllegalArgumentException as warranted.
     */
    public MaxTheKiddush(int[] bookings, int maxCapacity) {
        super(bookings, maxCapacity);
        if (maxCapacity < 1 || bookings == null || bookings.length < 1) throw new IllegalArgumentException();
        this.prefixSumArray = new int[bookings.length + 1];
        this.bookings = bookings;
        this.maxCapacity = maxCapacity;
        this.canBook = true;
        this.isUniform = true;


        for (int i = 1; i <= bookings.length; i++) {
            if (bookings[i - 1] < 1) throw new IllegalArgumentException(i + ": is not a positive int");
            if (bookings[i - 1] > maxCapacity) canBook = false;
            if (bookings[i - 1] != bookings[0]) isUniform = false;
            this.prefixSumArray[i] = this.prefixSumArray[i - 1] + bookings[i - 1];
        }
    }

    /**
     * Returns the maximum number of members (possibly zero) that can scheduled
     * on a given Shabbos to share the kiddush room such that all potential
     * bookings respect all constraints specified in the requirements document.
     */
    @Override
    public int maxIt() {
        if (!this.canBook) return 0;
        if (this.isUniform) {
            return (int) (double) (this.maxCapacity / this.bookings[0]);
        }

        int totalMembers = this.bookings.length;
        int maxMembersForKiddush = 0;

        for (int i = totalMembers - 1; i >= 0; i--) {
            int lenOfValidSA = getLenOfValidSA(i);
            boolean isValid = true;
            int j = 0;
            while (isValid && j + lenOfValidSA <= totalMembers) {
                if (j + lenOfValidSA <= this.bookings.length) {
                    int sumOfCurrentSA = this.prefixSumArray[j + lenOfValidSA] - this.prefixSumArray[j];
                    if (sumOfCurrentSA > this.maxCapacity) { isValid = false; }
                }
                j++;
            }
            if (isValid) { maxMembersForKiddush = Math.max(maxMembersForKiddush, lenOfValidSA); }
        }
        return maxMembersForKiddush;
    }

    private int getLenOfValidSA(int i) {
        int leftIndex = 0;
        int rightIndex = i;
        int bestStartIndex = i;

        while (leftIndex <= rightIndex) {
            int mid = (leftIndex + rightIndex) / 2;
            int sumOfSA = this.prefixSumArray[i + 1] - this.prefixSumArray[mid];

            if (sumOfSA <= this.maxCapacity) {
                bestStartIndex = mid;
                rightIndex = mid - 1;
            } else {
                leftIndex = mid + 1;
            }
        }

        return i - bestStartIndex + 1;
    }

}
