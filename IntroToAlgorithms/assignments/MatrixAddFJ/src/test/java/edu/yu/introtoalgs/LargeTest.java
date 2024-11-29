package edu.yu.introtoalgs;

import java.util.HashSet;
import java.util.Set;

public class LargeTest {


    public static void main(String[] args) {
        int size = 8192;

        double[][] a = new double[size][size];
        double[][] b = new double[size][size];



        testWith1MatrixAddPerOp(a, b);


        testWith1MatrixTotal(a, b);

        correctMatrix();

    }

    private static void correctMatrix() {

    }

    private static void testWith1MatrixTotal(double[][] a, double[][] b) {
        int higherThreshold = 50000;
        long highAvg = 0;
        int i;
        MatrixAddFJ matrixAdd = new MatrixAddFJ(higherThreshold);
        for (i = 1; i < 2 + 1; i++){
            long start = System.nanoTime();
            double[][] result = matrixAdd.add(a, b);
            //arrays.add(result);
            long end = System.nanoTime();
            highAvg += (end - start);
            //System.out.println("Time taken: " + (end - start) / 1e6 + " ms");
        }
        System.out.println("+--------------------------------+");
        System.out.printf("Higher threshold average: %.2f ms%n", (highAvg / (double) i) / 1e6);
        System.out.println("+--------------------------------+");
        System.out.println();


        int lowerThreshold = 5000;
        long lowAvg = 0;
        MatrixAddFJ matrixAdd2 = new MatrixAddFJ(lowerThreshold);
        for (i = 1; i < 2 + 1; i++){
            long start = System.nanoTime();
            double[][] result = matrixAdd2.add(a, b);
            //arrays.add(result);
            long end = System.nanoTime();
            lowAvg += (end - start);
            //System.out.println("Time taken: " + (end - start) / 1e6 + " ms");


        }
        System.out.println("+-------------------------------+");
        System.out.printf("Lower threshold average: %.2f ms%n", (lowAvg / (double) i) / 1e6);
        System.out.println("+-------------------------------+");
        System.out.println();


    }


    private static void testWith1MatrixAddPerOp(double[][] a, double[][] b) {
        int higherThreshold = 50000;
        long highAvg = 0;
        int i;
        for (i = 1; i < 50 + 1; i++){
            MatrixAddFJ matrixAdd = new MatrixAddFJ(higherThreshold);
            long start = System.nanoTime();
            double[][] result = matrixAdd.add(a, b);
            long end = System.nanoTime();
            highAvg += (end - start);
            //System.out.println("Time taken: " + (end - start) / 1e6 + " ms");
        }
        System.out.println("+--------------------------------+");
        System.out.printf("Higher threshold average: %.2f ms%n", (highAvg / (double) i) / 1e6);
        System.out.println("+--------------------------------+");
        System.out.println();

        int lowerThreshold = 5000;
        long lowAvg = 0;
        for (i = 1; i < 50 + 1; i++){
            MatrixAddFJ matrixAdd = new MatrixAddFJ(lowerThreshold);
            long start = System.nanoTime();
            double[][] result = matrixAdd.add(a, b);
            long end = System.nanoTime();
            lowAvg += (end - start);
            //System.out.println("Time taken: " + (end - start) / 1e6 + " ms");
        }
        System.out.println("+-------------------------------+");
        System.out.printf("Lower threshold average: %.2f ms%n", (lowAvg / (double) i) / 1e6);
        System.out.println("+-------------------------------+");
        System.out.println();
    }
}

