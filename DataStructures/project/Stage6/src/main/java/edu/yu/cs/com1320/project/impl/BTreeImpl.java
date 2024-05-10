package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.BTree;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class BTreeImpl<Key extends Comparable<Key>, Value> implements BTree<Key, Value> {
    protected PersistenceManager<Key, Value> serializer;
    private static final int MAX = 6;
    //root of the B-tree
    private Node root;
    private Node leftMostExternalNode;
    //height of the B-tree
    private int height;
    //number of key-value pairs in the B-tree
    private int n;

    private static final class Node {
        private int entryCount; // number of entries
        private Entry[] entries = new Entry[MAX]; // the array of children
        private Node next;
        private Node previous;

        // create a node with k entries
        private Node(int k) {
            this.entryCount = k;
        }

        private void setNext(Node next) {
            this.next = next;
        }
        private Node getNext() {
            return this.next;
        }
        private void setPrevious(Node previous) {
            this.previous = previous;
        }
        private Node getPrevious(){
            return this.previous;
        }

        private Entry[] getEntries() {
            return Arrays.copyOf(this.entries, this.entryCount);
        }

    }

    //internal nodes: only use key and child
    //external nodes: only use key and value
    private static class Entry {
        private Comparable key;
        private Object val;
        private Node child;

        public Entry(Comparable key, Object val, Node child) {
            this.key = key;
            this.val = val;
            this.child = child;
        }
        public Object getValue() {
            return this.val;
        }
        public Comparable getKey() {
            return this.key;
        }
    }

    public BTreeImpl(){
        this.serializer = null;
        this.root = new Node(0);
        this.leftMostExternalNode = this.root;
    }

    /**
     * @param k 
     * @return
     */
    @Override
    public Value get(Key k) {
        if (k == null) {
            throw new IllegalArgumentException("argument to get() is null");
        }
        Entry entry = this.get(this.root, k, this.height);
        if(entry == null){
            return null;
        }
        if(entry.val == null){
            //if val is null, Doc can either be on disk or non-existent/deleted
            try {
                //if able to deserialize then return serialization
                Value value = serializer.deserialize(k);
                return value;
            } catch (IOException e) {
                //If not then it is non-existent

                //this might cause problems during testing, the catch may also catch
                //bugs and not return valid deserialization due to bad deserializing
                return null;
            }
        }
        return (Value) entry.val;
    }

    private Entry get(Node currentNode, Key key, int height) {
        Entry[] entries = currentNode.entries;
        //current node is external (i.e. height == 0)
        if (height == 0) {
            for (int j = 0; j < currentNode.entryCount; j++) {
                //found desired key. Return its value
                if(isEqual(key, entries[j].key)) {
                    return entries[j];
                }
            }
            //didn't find the key
            return null;
        }
        //current node is internal (height > 0)
        else {
            for (int j = 0; j < currentNode.entryCount; j++) {
                //if (we are at the last key in this node OR the key we
                //are looking for is less than the next key, i.e. the
                //desired key must be in the subtree below the current entry),
                //then recurse into the current entry’s child
                if (j + 1 == currentNode.entryCount || less(key, entries[j + 1].key)) {
                    return this.get(entries[j].child, key, height - 1);
                }
            }
            //didn't find the key
            return null;
        }
    }

    /**
     * @param k 
     * @param v
     * @return
     */
    @Override
    public Value put(Key k, Value v)  {
        if (k == null)
            throw new IllegalArgumentException();
        //if the key already exists in the b-tree, simply replace the value
        Entry alreadyThere = this.get(this.root, k, this.height);
        //delete
        if(v == null && alreadyThere != null){
            //return null
            return delete(k, alreadyThere);
        };

        if (alreadyThere != null ){
            //I can assume that if a doc existed it is in memory and off the disk since any put call get

            //If there exists an entry there are 3 possibilities
            //1) The key has a non-null value in the tree. This means that either it was always in memory or it was
            //just brought into memory by the get method that called the put method.
            //2) If the value is null this means that a document existed but was deleted, or it was just called in from memory?
            //entry exists, need to replace it with the new value
            //if the value is null, a doc doesn't exist anymore and set new value
            return (Value) (alreadyThere.val = v);
        }
        Node newNode = this.put(this.root, k, v, this.height);
        this.n++;
        if (newNode == null) {
            return null;
        }
        Node newRoot = new Node(2);
        newRoot.entries[0] = new Entry(this.root.entries[0].key, null, this.root);
        newRoot.entries[1] = new Entry(newNode.entries[0].key, null, newNode);
        this.root = newRoot;
        //a split at the root always increases the tree height by 1
        this.height++;
        return v;
    }

    @Nullable
    private Value delete(Key k, Entry alreadyThere) {
        //value exists but is on disk
        if(alreadyThere.val == null){
            try {
                //delete from disk
                this.serializer.delete(k);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        alreadyThere.val = null;
        return null;
    }

    private Node put(Node currentNode, Key key, Value val, int height) {
        int j;
        Entry newEntry = new Entry(key, val, null);

        //external node
        if (height == 0) {
            //find index in currentNode’s entry[] to insert new entry
            //we look for key < entry.key since we want to leave j
            //pointing to the slot to insert the new entry, hence we want to find
            //the first entry in the current node that key is LESS THAN
            for (j = 0; j < currentNode.entryCount; j++) {
                if (less(key, currentNode.entries[j].key)) {
                    break;
                }
            }
            // internal node
        } else {
            //find index in node entry array to insert the new entry
            for (j = 0; j < currentNode.entryCount; j++) {
                //if (we are at the last key in this node OR the key we
                //are looking for is less than the next key, i.e. the
                //desired key must be added to the subtree below the current entry),
                //then do a recursive call to put on the current entry’s child
                if ((j + 1 == currentNode.entryCount) || less(key, currentNode.entries[j + 1].key)) {
                    //increment j (j++) after the call so that a new entry created by a split
                    //will be inserted in the next slot
                    Node newNode = this.put(currentNode.entries[j++].child, key, val, height - 1);
                    if (newNode == null) {
                        return null;
                    }
                    //if the call to put returned a node, it means I need to add a new entry to
                    //the current node
                    newEntry.key = newNode.entries[0].key;
                    newEntry.val = null;
                    newEntry.child = newNode;
                    break;
                }
            }
        }
        //shift entries over one place to make room for new entry
        for (int i = currentNode.entryCount; i > j; i--) {
            currentNode.entries[i] = currentNode.entries[i - 1];
        }
        //add new entry
        currentNode.entries[j] = newEntry;
        currentNode.entryCount++;
        if (currentNode.entryCount < MAX) {
            //no structural changes needed in the tree
            //so just return null
            return null;
        } else {
            //will have to create new entry in the parent due
            //to the split, so return the new node, which is
            //the node for which the new entry will be created
            return this.split(currentNode, height);
        }
    }

    /**
     * split node in half
     * @param currentNode
     * @return new node
     */
    private Node split(Node currentNode, int height)
    {
        Node newNode = new Node(MAX / 2);
        //by changing currentNode.entryCount, we will treat any value
        //at index higher than the new currentNode.entryCount as if
        //it doesn't exist
        currentNode.entryCount = MAX / 2;
        //copy top half of h into t
        for (int j = 0; j < MAX / 2; j++)
        {
            newNode.entries[j] = currentNode.entries[MAX / 2 + j];
        }
        //external node
        if (height == 0)
        {
            newNode.setNext(currentNode.getNext());
            newNode.setPrevious(currentNode);
            currentNode.setNext(newNode);
        }
        return newNode;
    }

    /**
     * @param k 
     * @throws IOException
     */
    @Override
    public void moveToDisk(Key k) throws IOException {
        if (k == null)
            throw new IllegalArgumentException();
        if (this.get(k) == null)
            throw new IllegalArgumentException();
        this.serializer.serialize(k, this.get(k));
        this.putAfterMove(k);
    }

    private void putAfterMove(Key k){
        Entry alreadyThere = this.get(this.root, k, this.height);
        alreadyThere.val = null;
    }

    /**
     * @param pm 
     */
    @Override
    public void setPersistenceManager(PersistenceManager<Key, Value> pm) {
        this.serializer = pm;
    }


    private ArrayList<Entry> getOrderedEntries() {
        Node current = this.leftMostExternalNode;
        ArrayList<Entry> entries = new ArrayList<>();
        while(current != null) {
            for(Entry e : current.getEntries()) {
                if(e.val != null) {
                    entries.add(e);
                }
            }
            current = current.getNext();
        }
        return entries;
    }

    private Entry getMinEntry() {
        Node current = this.leftMostExternalNode;
        while(current != null) {
            for(Entry e : current.getEntries()) {
                if(e.val != null) {
                    return e;
                }
            }
        }
        return null;
    }

    private Entry getMaxEntry() {
        ArrayList<Entry> entries = this.getOrderedEntries();
        return entries.get(entries.size()-1);
    }

    private boolean less(Comparable k1, Comparable k2) {
        return k1.compareTo(k2) < 0;
    }

    private boolean isEqual(Comparable k1, Comparable k2) {
        return k1.compareTo(k2) == 0;
    }

    private boolean isEmpty() {
        return this.size() == 0;
    }

    private int size() {
        return this.n;
    }

    private int height() {
        return this.height;
    }
}
