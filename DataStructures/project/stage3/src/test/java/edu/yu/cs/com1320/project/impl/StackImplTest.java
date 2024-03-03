package edu.yu.cs.com1320.project.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StackImplTest {
    private StackImpl<Integer> integerStack;
    @BeforeEach
    void setUp() {
        this.integerStack = new StackImpl<>();
    }

    @Test
    void push() {
        this.integerStack.push(1);
        this.integerStack.push(2);
        this.integerStack.push(3);
        this.integerStack.push(4);
        this.integerStack.push(5);
        assertEquals(5, this.integerStack.peek());
    }

    @Test
    void pop() {
        this.integerStack.push(1);
        this.integerStack.push(2);
        this.integerStack.push(3);
        this.integerStack.push(4);
        this.integerStack.push(5);
        assertEquals(5, this.integerStack.pop());
    }

    @Test
    void pop_nullInput() {
        assertNull(this.integerStack.pop());
    }


    @Test
    void peek() {
        this.integerStack.push(1);
        this.integerStack.push(2);
        this.integerStack.push(3);
        this.integerStack.push(4);
        this.integerStack.push(5);
        assertEquals(5, this.integerStack.peek());
    }

    @Test
    void size() {
        int i;
        for (i = 0; i < 25; i++) {
            this.integerStack.push(i);
        }
        assertEquals(i, this.integerStack.size());
    }

    @Test
    void removeAll(){
        int i;
        for (i = 0; i < 25; i++) {
            this.integerStack.push(i);
        }
        while (this.integerStack.size() > 0){
            int num = this.integerStack.pop();
            assert(num == --i);
        }
    }
}