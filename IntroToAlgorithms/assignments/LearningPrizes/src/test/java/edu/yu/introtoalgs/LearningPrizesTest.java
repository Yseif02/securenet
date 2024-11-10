package edu.yu.introtoalgs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LearningPrizesTest {
    LearningPrizesBase learningPrizes;

    @BeforeEach
    void setUp() {
        this.learningPrizes = new LearningPrizes(1.0);
    }

    @Test
    void addTicket() {
        this.learningPrizes.addTicket(1, 1, 1.0);
        this.learningPrizes.addTicket(1, 2, 2.0);
        this.learningPrizes.addTicket(1, 3, 3.0);

    }

    @Test
    void addTicketMultipleDays() {
        this.learningPrizes.addTicket(1, 1, 4.0);
        this.learningPrizes.addTicket(1, 2, 5.0);
        this.learningPrizes.addTicket(1, 3, 6.0);

        this.learningPrizes.addTicket(2, 1, 7.0);
        this.learningPrizes.addTicket(2, 2, 8.0);
        this.learningPrizes.addTicket(2, 3, 9.0);

        this.learningPrizes.addTicket(3, 1, 10.0);
        this.learningPrizes.addTicket(3, 2, 11.0);
        this.learningPrizes.addTicket(3, 3, 6.0);
        this.learningPrizes.addTicket(3, 4, 13.0);
    }

    @Test
    void addInValidTickets() {


        assertThrows(IllegalArgumentException.class, () -> {
            this.learningPrizes.addTicket(-1, 1, 4.0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            this.learningPrizes.addTicket(0, 1, 4.0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            this.learningPrizes.addTicket(1, -1, 5.0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            this.learningPrizes.addTicket(2, 1, -2.3);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            this.learningPrizes.addTicket(2, 1, 0);
        });
    }

    @Test
    void addMultipleTicketsSameDaySameId(){
        this.learningPrizes.addTicket(1, 1, 4.0);
        this.learningPrizes.addTicket(1, 1, 5.0);
        this.learningPrizes.addTicket(1, 1, 6.0);
    }

    @Test
    void addTicketForPreviousDay(){
        this.learningPrizes.addTicket(1, 1, 4.0);
        this.learningPrizes.addTicket(2, 1, 5.0);
        this.learningPrizes.addTicket(1, 1, 6.0);
    }

    @Test
    void awardedPrizeMoney() {

    }

    @Test
    void testIterator(){
        day1Tickets();
        day2Tickets();
        day3Tickets();
        day4Tickets();
        day5Tickets();

        var iterator = this.learningPrizes.awardedPrizeMoney();
        assertTrue(iterator.hasNext());
        assertEquals(14.6, iterator.next());
        assertEquals(10.2, iterator.next());
        assertEquals(10.4, iterator.next());
        assertEquals(8, iterator.next());
        assertEquals(7.2, iterator.next());
    }

    @Test
    void test2Iterations(){
        day1Tickets();
        day2Tickets();
        day3Tickets();

        var iterator = this.learningPrizes.awardedPrizeMoney();
        assertTrue(iterator.hasNext());
        assertEquals(15, iterator.next());
        assertEquals(10.6, iterator.next());
        assertEquals(10.5, iterator.next());


        day4Tickets();
        day5Tickets();
        day6Tickets();

        var iterator2 = this.learningPrizes.awardedPrizeMoney();
        assertEquals(8, iterator2.next());
        assertEquals(7.5, iterator2.next());
        assertEquals(7.699999999999999, iterator2.next());

    }

    @Test
    void test2IterationsBefore1finishes(){
        day1Tickets();
        day2Tickets();
        day3Tickets();

        var iterator = this.learningPrizes.awardedPrizeMoney();
        assertTrue(iterator.hasNext());
        assertEquals(15, iterator.next());

        day4Tickets();
        day5Tickets();
        day6Tickets();

        var iterator2 = this.learningPrizes.awardedPrizeMoney();
        assertEquals(8, iterator2.next());
        assertEquals(10.6, iterator.next());
        assertEquals(7.5, iterator2.next());
        assertEquals(10.5, iterator.next());
        assertEquals(7.699999999999999, iterator2.next());

    }

    @Test
    void testLessThan2Tickets(){
        this.learningPrizes.addTicket(1, 1, 2);
        this.learningPrizes.addTicket(1, 2, 4);
        this.learningPrizes.addTicket(1, 3, 6);
        this.learningPrizes.addTicket(2, 1, 2);
        this.learningPrizes.addTicket(4, 2, 4);
    }

    private void day1Tickets() {
        this.learningPrizes.addTicket(1, 1, 4.0);
        this.learningPrizes.addTicket(1, 2, 5.0);
        this.learningPrizes.addTicket(1, 3, 6.0);
        this.learningPrizes.addTicket(1, 4, 16.0); // removed day 1
        this.learningPrizes.addTicket(1, 5, 6.4);
        this.learningPrizes.addTicket(1, 6, 8.2); // removed day 6
        this.learningPrizes.addTicket(1, 7, 5.7);
        this.learningPrizes.addTicket(1, 8, 4.9);
        this.learningPrizes.addTicket(1, 9, 11.6); // removed day 2
        this.learningPrizes.addTicket(1, 10, 1); // removed day 1
        this.learningPrizes.addTicket(1, 11, 2.2);
    }

    private void day2Tickets() {
        this.learningPrizes.addTicket(2, 5, 6.4);
        this.learningPrizes.addTicket(2, 10, 11.5); // removed day 3
        this.learningPrizes.addTicket(2, 4, 1); // removed day 2
        this.learningPrizes.addTicket(2, 1, 2.7);
        this.learningPrizes.addTicket(2, 8, 4.9);
        this.learningPrizes.addTicket(2, 2, 5.1);
        this.learningPrizes.addTicket(2, 11, 2.5);
        this.learningPrizes.addTicket(2, 9, 1.5);
        this.learningPrizes.addTicket(2, 3, 6.0);
        this.learningPrizes.addTicket(2, 6, 4);
        this.learningPrizes.addTicket(2, 7, 6);
    }

    private void day3Tickets() {
        this.learningPrizes.addTicket(3, 10, 2);
        this.learningPrizes.addTicket(3, 3, 1); // removed day 3
        this.learningPrizes.addTicket(3, 2, 3.5);
        this.learningPrizes.addTicket(3, 9, 9); // removed day 4
        this.learningPrizes.addTicket(3, 5, 1.5);
        this.learningPrizes.addTicket(3, 7, 2);
        this.learningPrizes.addTicket(3, 6, 2);
        this.learningPrizes.addTicket(3, 11, 2.5);
        this.learningPrizes.addTicket(3, 8, 1);
        this.learningPrizes.addTicket(3, 1, 2);
        this.learningPrizes.addTicket(3, 4, 1); // removed day 4
    }

    private void day4Tickets() {
        this.learningPrizes.addTicket(4, 2, 2.5);
        this.learningPrizes.addTicket(4, 5, 3.5);
        this.learningPrizes.addTicket(4, 10, 1);
        this.learningPrizes.addTicket(4, 8, 1);
        this.learningPrizes.addTicket(4, 9, 8.5); // removed day 5
        this.learningPrizes.addTicket(4, 7, 1);
        this.learningPrizes.addTicket(4, 6, 1);
        this.learningPrizes.addTicket(4, 11, 1.5);
        this.learningPrizes.addTicket(4, 3, 1); // removed day 5
        this.learningPrizes.addTicket(4, 1, 2.2);
        this.learningPrizes.addTicket(4, 4, 1);
    }

    private void day5Tickets() {
        this.learningPrizes.addTicket(5, 1, 1.1);
        this.learningPrizes.addTicket(5, 6, 2.2);
        this.learningPrizes.addTicket(5,2, 3.3);
        this.learningPrizes.addTicket(5, 8, 4.4);
        this.learningPrizes.addTicket(5, 10, 5.5);
        this.learningPrizes.addTicket(5, 3, 6.6);
        this.learningPrizes.addTicket(5, 7, 1.2);
        this.learningPrizes.addTicket(5,4, 1.3);
        this.learningPrizes.addTicket(5,11, 1);
        this.learningPrizes.addTicket(5, 9, 1.4);
        this.learningPrizes.addTicket(5, 5, 5);
    }

    private void day6Tickets() {
        this.learningPrizes.addTicket(6, 1, 1.5);
        this.learningPrizes.addTicket(6, 2, 2.5);
        this.learningPrizes.addTicket(6, 3, 3);
        this.learningPrizes.addTicket(6, 4, 4);
        this.learningPrizes.addTicket(6, 5, 4.5);
        this.learningPrizes.addTicket(6, 6, 5);
        this.learningPrizes.addTicket(6, 7, 1);
        this.learningPrizes.addTicket(6, 8, 2);
        this.learningPrizes.addTicket(6, 9, 1.5);
        this.learningPrizes.addTicket(6, 10, .5); // removed day 6
        this.learningPrizes.addTicket(6, 11, 1);
    }

    private void addRandomTickets(int day, int numTickets){
        Random random = new Random();
        for (int i = 1; i <= numTickets ; i++) {
            this.learningPrizes.addTicket(day, i, random.nextDouble() * 10);
        }
    }

    @Test
    void testAddingPerformance(){
        long start = System.currentTimeMillis();
        addRandomTickets(1, 1000000);
        addRandomTickets(2, 1000000);
        addRandomTickets(3, 1000000);
        addRandomTickets(4, 1000000);
        addRandomTickets(5, 1000000);
        addRandomTickets(6, 1000000);
        addRandomTickets(7, 1000000);
        addRandomTickets(8, 1000000);
        addRandomTickets(9, 1000000);
        addRandomTickets(10, 1000000);
        addRandomTickets(11, 1000000);
        long end = System.currentTimeMillis();
        System.out.printf("Time taken: %.3fms%n", (end - start) / 1000.0);
    }

    @Test
    void testAddingPerformanceLittleAmount(){
        long start = System.currentTimeMillis();
        addRandomTickets(1, 65536);
        addRandomTickets(2, 65536);
        addRandomTickets(3, 65536);
        addRandomTickets(4, 65536);
        addRandomTickets(5, 65536);
        addRandomTickets(6, 65536);
        addRandomTickets(7, 65536);
        addRandomTickets(8, 65536);
        addRandomTickets(9, 65536);
        addRandomTickets(10, 65536);
        addRandomTickets(11, 65536);
        long end = System.currentTimeMillis();
        System.out.printf("Time taken: %.3fms%n", (end - start) / 1000.0);
    }

    @Test
    void testIteratorPerformance(){
        for (int i = 1; i <= 1100000; i++) {
            addRandomTickets(i, 2);
        }

        long start = System.currentTimeMillis();
        var iterator = this.learningPrizes.awardedPrizeMoney();
        int count = 0;
        while (iterator.hasNext()){
            System.out.println(iterator.next());
            count++;
        }
        long end = System.currentTimeMillis();
        System.out.printf("Time taken: %.3fms%n", (end - start) / 1000.0);
        System.out.println(count);

    }






}