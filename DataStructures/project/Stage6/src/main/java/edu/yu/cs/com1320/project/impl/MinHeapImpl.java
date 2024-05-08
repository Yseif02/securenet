package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.MinHeap;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class MinHeapImpl<E extends Comparable<E>> extends MinHeap<E> {
    public MinHeapImpl(){
        super.elements = (E[]) new Comparable[10];
    }

    /**
     * @param element
     */
    public void reHeapify(E element) {
        if (element == null){
            throw new IllegalArgumentException();
        }
        int arrayIndex = getArrayIndex(element);
        if(arrayIndex == -1) return; //element doesn't exist
        if(arrayIndex == 0) return; //should not happen
        if(arrayIndex == 1) downHeap(arrayIndex);
        else{
            E thisElement = this.elements[arrayIndex];
            E parent = this.elements[arrayIndex/2];
            if(parent.compareTo(thisElement) > 0){
                upHeap(arrayIndex);
            } else{
                downHeap(arrayIndex);
            }
        }
        /*if(elements[arrayIndex].compareTo(elements[arrayIndex/2]) < 0) {
            this.upHeap(arrayIndex);
        }*/
    }

    /**
     * @param element
     * @return
     */
    protected int getArrayIndex(E element) {
        if (element == null){
            throw new IllegalArgumentException();
        }
        for(int i = 1; i <= this.count; i++){
            if(elements[i] == element) return i;
        }
        throw new NoSuchElementException();
    }

    /**
     *
     */
    protected void doubleArraySize() {
        this.elements = Arrays.copyOf(this.elements, this.elements.length * 2);
    }
}
