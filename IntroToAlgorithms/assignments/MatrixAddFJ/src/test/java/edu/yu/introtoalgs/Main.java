package edu.yu.introtoalgs;

public class Main {
    public static void main(String[] args) {
        double[][] a = {
                {1, 2, 3, 4, 5, 6, 7, 8},
                {9, 10, 11, 12, 13, 14, 15, 16},
                {17, 18, 19, 20, 21, 22, 23, 24},
                {25, 26, 27, 28, 29, 30, 31, 32},
                {33, 34, 35, 36, 37, 38, 39, 40},
                {41, 42, 43, 44, 45, 46, 47, 48},
                {49, 50, 51, 52, 53, 54, 55, 56},
                {57, 58, 59, 60, 61, 62, 63, 64}
        };

        double[][] b = {
                {64, 63, 62, 61, 60, 59, 58, 57},
                {56, 55, 54, 53, 52, 51, 50, 49},
                {48, 47, 46, 45, 44, 43, 42, 41},
                {40, 39, 38, 37, 36, 35, 34, 33},
                {32, 31, 30, 29, 28, 27, 26, 25},
                {24, 23, 22, 21, 20, 19, 18, 17},
                {16, 15, 14, 13, 12, 11, 10, 9},
                {8, 7, 6, 5, 4, 3, 2, 1}
        };

// Test with threshold 8
        MatrixAddFJ matrixAdd8 = new MatrixAddFJ(8);
        long startTime8 = System.nanoTime();
        double[][] result8 = matrixAdd8.add(a, b);
        long endTime8 = System.nanoTime();
        long duration8 = endTime8 - startTime8;
        System.out.println("Time with threshold 8: " + duration8 + " nanoseconds");

// Test with threshold 4
        MatrixAddFJ matrixAdd4 = new MatrixAddFJ(4);
        long startTime4 = System.nanoTime();
        double[][] result4 = matrixAdd4.add(a, b);
        long endTime4 = System.nanoTime();
        long duration4 = endTime4 - startTime4;
        System.out.println("Time with threshold 4: " + duration4 + " nanoseconds");

// Print result for threshold 4
        for (double[] row : result4) {
            for (double val : row) {
                System.out.print(val + " ");
            }
            System.out.println();
        }
    }
}