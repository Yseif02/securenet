package edu.yu.cs.com1320.project.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MinHeapImplTest {
    private MinHeapImpl<Integer> heap;
    private Comparable[] elements;
    private int count;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        this.heap = new MinHeapImpl<>();
        Class<?> heapClass = heap.getClass().getSuperclass();
        Field elementsField = heapClass.getDeclaredField("elements");
        elementsField.setAccessible(true);
        /*Field countField = heapClass.getField("count");
        countField.setAccessible(true);*/
        this.elements = (Comparable[]) elementsField.get(heap);
        //this.count = (int) countField.get(heap);
    }


    @Test
    void isEmpty() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assertNull(heap.peek());
        heap.insert(1);
        assertNotNull(heap.peek());
    }

    @Test
    void isGreater() throws NoSuchFieldException, IllegalAccessException {
        Class<?> heapClass = heap.getClass().getSuperclass();
        Field elements = heapClass.getDeclaredField("elements");
        elements.setAccessible(true);
        Comparable[] newElements = (Comparable[]) elements.get(heap);
        heap.insert(1);
        heap.insert(2);
        int one = heap.getArrayIndex(1);
        int two = heap.getArrayIndex(2);
        assertTrue(newElements[two].compareTo(newElements[one]) > 0);
        //assertTrue(newElements[heap.getArrayIndex(2)].compareTo(newElements[heap.getArrayIndex(2)]) > 0);
    }

    @Test
    void swap() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        heap.insert(1);
        heap.insert(2);
        int one = heap.getArrayIndex(1);
        int two = heap.getArrayIndex(2);
        /*Class<?> heapClass = heap.getClass().getSuperclass();
        Method method = heapClass.getDeclaredMethod("swap");
        method.setAccessible(true);
        method.invoke(heapClass, one, two);*/
        assertTrue(elements[two].compareTo(elements[one]) < 0);
    }

    @Test
    void upHeap() {
    }

    @Test
    void downHeap() {
    }

    @Test
    void insert() throws NoSuchFieldException, IllegalAccessException {
        Class<?> heapClass = heap.getClass().getSuperclass();
        Field countField = heapClass.getDeclaredField("count");
        countField.setAccessible(true);
        heap.insert(4);
        assertEquals(1, (int) countField.get(heap));
        heap.insert(3);
        assertEquals(2, (int) countField.get(heap));
        heap.insert(2);
        assertEquals(3, (int) countField.get(heap));
        heap.insert(1);
        assertEquals(4, (int) countField.get(heap));
    }

    @Test
    void peek() {
    }

    @Test
    void remove() {
    }

    @Test
    void reHeapify() {
    }

    @Test
    void getArrayIndex() {
    }

    @Test
    void doubleArraySize() {
    }
}