package edu.yu.introtoalgs;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import static java.util.concurrent.ForkJoinTask.invokeAll;

public class MatrixAddFJ extends MatrixAddFJBase{
    private final int threshold;
    //private final ForkJoinPool pool;
    private static final ForkJoinPool POOL = new ForkJoinPool();

    /**
     * Constructor: client specifies the threshold value "n" (in a "n by n"
     * matrix) at which a Fork-Join implementation of add should switch over to a
     * serial implementation.
     *
     * @param addThreshold specifies that matrix addition for "n" greater than or
     *                     equal to the threshold must be processed using a serial algorithm rather
     *                     than via FJ decomposition, must be greater than 0.
     */
    public MatrixAddFJ(int addThreshold) {
        super(addThreshold);
        if (addThreshold < 1) throw new IllegalArgumentException("Threshold must be greater than zero");
        this.threshold = addThreshold;
    }

    /**
     * Adds matrices a and b.  The implementation MUST be based on using the
     * JDK's Fork-Join (either RecursiveTask or RecursiveAction classes).  The FJ
     * threshold is supplied by the constructor.
     * <p>
     * NOTE: if the addThreshold is greater than or equal to the size of the add
     * threshold, the implementation MUST use the straightforward serial
     * algorithm.
     *
     * @param a Represents a "matrix", n by n, client maintains ownership.  It is
     *          the client's responsibility to ensure that the matrix is square and the
     *          same dimensions as the other parameter.
     * @param b Represents a "matrix", n by n, client maintains ownership.  It is
     *          the client's responsibility to ensure that the matrix is square and the
     *          same dimensions as the other parameter.
     * @return Result of adding the two matrices.
     */
    @Override
    public double[][] add(double[][] a, double[][] b) {
        int aLen = a.length;
        int bLen = b.length;

        //if (aLen < 1 || aLen != bLen || (bLen & (bLen - 1)) != 0) throw new IllegalArgumentException("Matrices must be of the same size and a power of 2");

        double[][] resultMatrix = new double[aLen][aLen];
        if (this.threshold >= aLen) {
            return addSerial(a, b, resultMatrix, 0, 0, 0, 0, aLen);
        }
        POOL.invoke(new SplitMatrixIn4Task(a, b, resultMatrix, 0, 0, 0, 0, 0, 0, aLen));
        return resultMatrix;
    }

    private double[][] addSerial(double[][] a, double[][] b, double[][] resultMatrix, int rowA, int colA, int rowB, int colB, int size) {
        int i = 0;
        while (i < size) {
            int j = 0;
            while (j < size) {
                resultMatrix[i][j] = a[rowA + i][colA + j] + b[rowB + i][colB + j];
                j++;
            }
            i++;
        }
        return resultMatrix;
    }

    private class SplitMatrixIn4Task extends RecursiveTask<Void> {
        private final double[][] matrixA;
        private final double[][] matrixB;
        private final double[][] resultMatrix;
        private final int startColA;
        private final int startRowA;
        private final int startColB;
        private final int startRowB;
        private final int size;
        private final int resultRow;
        private final int resultCol;

        public SplitMatrixIn4Task(double[][] matrixA, double[][] matrixB, double[][] resultMatrix, int startRowA, int startColA, int startRowB, int startColB, int resultRow, int resultCol, int size) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.resultMatrix = resultMatrix;
            this.startRowA = startRowA;
            this.startColA = startColA;
            this.startRowB = startRowB;
            this.startColB = startColB;
            this.resultRow = resultRow;
            this.resultCol = resultCol;
            this.size = size;
        }

        @Override
        protected Void compute() {
            if (size <= threshold) {
                for (int i = 0; i < size; i++) {
                    System.arraycopy(matrixA[startRowA + i], startColA, resultMatrix[resultRow + i], resultCol, size);
                    for (int j = 0; j < size; j++) {
                        resultMatrix[resultRow + i][resultCol + j] += matrixB[startRowB + i][startColB + j];
                    }
                }
                return null;
            }

            int newSize = size / 2;

            SplitMatrixIn4Task topLeftMatrix = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA, startColA, startRowB, startColB, resultRow, resultCol, newSize);
            SplitMatrixIn4Task topRightMatrix = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA, startColA + newSize, startRowB, startColB + newSize, resultRow, resultCol + newSize, newSize);
            SplitMatrixIn4Task bottomLeftMatrix = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA + newSize, startColA, startRowB + newSize, startColB, resultRow + newSize, resultCol, newSize);
            SplitMatrixIn4Task bottomRightMatrix = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA + newSize, startColA + newSize, startRowB + newSize, startColB + newSize, resultRow + newSize, resultCol + newSize, newSize);
            invokeAll(topLeftMatrix, topRightMatrix, bottomLeftMatrix, bottomRightMatrix);

            /*SplitMatrixIn4Task topLeftTask = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA, startColA, startRowB, startColB, resultRow, resultCol, newSize);
            SplitMatrixIn4Task topRightTask = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA, startColA + newSize, startRowB, startColB + newSize, resultRow, resultCol + newSize, newSize);

            topLeftTask.fork();
            topRightTask.compute();
            topLeftTask.join();

            SplitMatrixIn4Task bottomLeftTask = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA + newSize, startColA, startRowB + newSize, startColB, resultRow + newSize, resultCol, newSize);
            SplitMatrixIn4Task bottomRightTask = new SplitMatrixIn4Task(matrixA, matrixB, resultMatrix, startRowA + newSize, startColA + newSize, startRowB + newSize, startColB + newSize, resultRow + newSize, resultCol + newSize, newSize);

            bottomLeftTask.fork();
            bottomRightTask.compute();
            bottomLeftTask.join();*/

            //combineResults(resultMatrix, topLeftMatrix.join(), topRightMatrix.join(), bottomLeftMatrix.join(), bottomRightMatrix.join(), newSize);
            return null;
        }

        /*private void combineResults(double[][] result, double[][] topLeft, double[][] topRight, double[][] bottomLeft, double[][] bottomRight, int newSize) {
            int i = 0;
            while (i < newSize) {
                System.arraycopy(topLeft[i], 0, result[i], 0, newSize);
                System.arraycopy(topRight[i], 0, result[i], newSize, newSize);
                System.arraycopy(bottomLeft[i], 0, result[i + newSize], 0, newSize);
                System.arraycopy(bottomRight[i], 0, result[i + newSize], newSize, newSize);
                i++;
            }
        }*/
    }
}
