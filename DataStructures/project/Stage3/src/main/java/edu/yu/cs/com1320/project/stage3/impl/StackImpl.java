package edu.yu.cs.com1320.project.stage3.impl;

import edu.yu.cs.com1320.project.Stack;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class StackImpl<T> implements Stack<T>{
    private T[] stack;
    private int size;

    public StackImpl() {
        this.stack = (T[]) new Object[5];
        this.size = 0;
    }

    /**
     * @param element object to add to the Stack
     */
    @Override
    public void push(T element) {
        if (size() == this.stack.length) doubleArray();
        for (int i = 0; i < this.stack.length; i++) {
            if (this.stack[i] == null) {
                this.stack[i] = element;
                size++;
                return;
            }
        }
    }

    private void doubleArray() {
        this.stack = Arrays.copyOf(this.stack, this.size * 2);
    }

    /**
     * removes and returns element at the top of the stack
     *
     * @return element at the top of the stack, null if the stack is empty
     */
    @Override
    public T pop() {
        for (int i = this.stack.length - 1; i >= 0; i--) {
            if (this.stack[i] != null) {
                T valueToReturn = this.stack[i];
                this.stack[i] = null;
                size--;
                return valueToReturn;
            }
        }
        return null;
    }

    /**
     * @return the element at the top of the stack without removing it
     */
    @Override
    public T peek() {
        for (int i = this.stack.length - 1; i >= 0; i--) {
            if (this.stack[i] != null) {
                return this.stack[i];
            }
        }
        return null;
    }

    /**
     * @return how many elements are currently in the stack
     */
    @Override
    public int size() {
        int counter = 0;
        for (T element : this.stack) {
            if (element != null) counter++;
        }
        return counter;
    }

}
