package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Trie;

import java.util.*;

public class TrieImpl<Value> implements Trie<Value> {
    private static final int alphabetSize = 256; // extended ASCII
    private Node<Value> root; // root of trie
    private int wordsInTrie;

    public static class Node<Value> {
        private String valueString;
        protected Set<Value> documents = new HashSet<>();
        protected Node[] links = new Node[alphabetSize];
        private int amountOfLinks = 0;
        private boolean isWord = false;
    }

    public TrieImpl(){
        this.root = new Node<>();
        this.wordsInTrie = 0;
    }

    /**
     * add the given value at the given key
     *
     * @param key
     * @param val
     */
    @Override
    public void put(String key, Value val) {
        if(key == null || key.isEmpty()) throw new IllegalArgumentException();
        put(this.root, key, 0, val);
        wordsInTrie++;
    }

    private Node<Value> put(Node<Value> node, String key, int d, Value value){
        if(node == null){
            node = new Node<>();
        }
        if(d == key.length()){
            node.valueString = key;
            node.isWord = true;
            node.documents.add(value);
            return node;
        }
        char c = key.charAt(d);
        node.links[c] = this.put(node.links[c], key, d+1, value);
        //node.amountOfLinks++;
        return node;
    }


    /**
     * get all exact matches for the given key.
     * Search is CASE SENSITIVE.
     *
     * @param key
     * @return a Set of matching Values. Empty set if no matches.
     */
    @Override
    public Set<Value> get(String key) {
        Node x = this.get(this.root, key, 0);
        return (x == null) ? new HashSet<>() : x.documents;
    }

    private Node get(Node x, String key, int d) {
        //link was null - return null, indicating a miss
        if (x == null) {
            return null;
        }
        //we've reached the last node in the key,
        //return the node
        if (d == key.length()) {
            return x;
        }
        //proceed to the next node in the chain of nodes that
        //forms the desired key
        char c = key.charAt(d);
        return this.get(x.links[c], key, d + 1);
    }

    /**
     * Get all exact matches for the given key, sorted in descending order, where "descending" is defined by the comparator.
     * NOTE FOR COM1320 PROJECT: FOR PURPOSES OF A *KEYWORD* SEARCH, THE COMPARATOR SHOULD DEFINE ORDER AS HOW MANY TIMES THE KEYWORD APPEARS IN THE DOCUMENT.
     * Search is CASE SENSITIVE.
     *
     * @param key
     * @param comparator used to sort values
     * @return a List of matching Values. Empty List if no matches.
     */
    @Override
    public List<Value> getSorted(String key, Comparator<Value> comparator) {
        List<Value> listToReturn = new ArrayList<>(get(key).stream().toList());
        listToReturn.sort(comparator);
        return listToReturn;
    }

    /**
     * get all matches which contain a String with the given prefix, sorted in descending order, where "descending" is defined by the comparator.
     * NOTE FOR COM1320 PROJECT: FOR PURPOSES OF A *KEYWORD* SEARCH, THE COMPARATOR SHOULD DEFINE ORDER AS HOW MANY TIMES THE KEYWORD APPEARS IN THE DOCUMENT.
     * For example, if the key is "Too", you would return any value that contains "Tool", "Too", "Tooth", "Toodle", etc.
     * Search is CASE SENSITIVE.
     *
     * @param prefix
     * @param comparator used to sort values
     * @return a List of all matching Values containing the given prefix, in descending order. Empty List if no matches.
     */
    @Override
    public List<Value> getAllWithPrefixSorted(String prefix, Comparator<Value> comparator) {
        Node prefixNode = getNode(this.root, prefix, 0);
        assert prefixNode != null;
        Set<Value> documentSet = new HashSet<>();
        documentSet = this.getPrefixDocumentSet(prefixNode, documentSet);
        List<Value> documentListToReturn = new ArrayList<>(documentSet);
        documentListToReturn.sort(comparator);
        return documentListToReturn;
    }
    private Node getNode(Node node, String key, int depth){
        if (node == null) return null;
        if(depth == key.length()) return node;
        char currentChar = key.charAt(depth);
        return this.get(node.links[currentChar], key, depth + 1);
    }

    private Set<Value> getPrefixDocumentSet(Node node, Set<Value> documentSet){
        if(node == null) return documentSet;
        if(!node.documents.isEmpty()) documentSet.addAll(node.documents);
        if(Arrays.stream(node.links).allMatch(Objects::isNull)) return documentSet;
        for (Node childNode : node.links){
            getPrefixDocumentSet(childNode, documentSet);
        }
        return documentSet;
    }
    /**
     * Delete the subtree rooted at the last character of the prefix.
     * Search is CASE SENSITIVE.
     *
     * @param prefix
     * @return a Set of all Values that were deleted.
     */
    @Override
    public Set<Value> deleteAllWithPrefix(String prefix) {
        return null;
    }

    /**
     * Delete all values from the node of the given key (do not remove the values from other nodes in the Trie)
     *
     * @param key
     * @return a Set of all Values that were deleted.
     */
    @Override
    public Set<Value> deleteAll(String key) {
        return null;
    }

    /**
     * Remove the given value from the node of the given key (do not remove the value from other nodes in the Trie)
     *
     * @param key
     * @param val
     * @return the value which was deleted. If the key did not contain the given value, return null.
     */
    @Override
    public Value delete(String key, Value val) {
        return null;
    }
}