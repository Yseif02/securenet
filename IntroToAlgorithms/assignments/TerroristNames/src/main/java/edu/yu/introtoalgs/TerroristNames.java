package edu.yu.introtoalgs;

import java.util.HashMap;
import java.util.HashSet;

public class TerroristNames extends TerroristNamesBase{
    private final SuffixTreeNode root;
    private final HashSet<String> allWords;

    public TerroristNames(){
        super();
        this.root = new SuffixTreeNode();
        this.allWords = new HashSet<>();
    }

    /**trades
     * Adds a member to the set of current members.
     *
     * @param id@throws IllegalArgumentException if pre-conditions are violated.
     */
    @Override
    public void add(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH || this.allWords.contains(id)) throw new IllegalArgumentException();
        char[] idChars = id.toCharArray();
        for (char c : idChars) {
            if (c == ' ' || c == '\t') throw new IllegalArgumentException();
        }
        for (int i = 0; i < id.length(); i++) {
            addSuffix(id.substring(i));
        }

        this.allWords.add(id);
    }

    private void addSuffix(String suffix) {
        SuffixTreeNode currentNode = root;
        for (char c : suffix.toCharArray()) {
            SuffixTreeNode nextNode = currentNode.children.get(c);
            if (nextNode == null) {
                nextNode = new SuffixTreeNode();
                currentNode.children.put(c, nextNode);
            }
            currentNode = nextNode;
            currentNode.count++;
        }
    }

    /**
     * Returns the number of current members whose ids are a substring of the
     * specified id.
     *
     * @param id need not be a current id, but may not be empty, may not contain
     *           whitespace, and length may not exceed MAX_ID_LENGTH.
     * @return the number of current members whose id are a substring of the
     * specified id.
     * @throws IllegalArgumentException if pre-conditions are violated.
     */
    @Override
    public int search(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException();
        char[] idChars = id.toCharArray();
        for (char c : idChars) {
            if (c == ' ' || c == '\t') throw new IllegalArgumentException();
        }
        SuffixTreeNode currentNode = root;
        for (char c : id.toCharArray()) {
            currentNode = currentNode.children.get(c);
            if (currentNode == null) {
                return 0;
            }
        }

        return (this.allWords.contains(id)) ? currentNode.count - 1 : currentNode.count;
    }

    public static class SuffixTreeNode {
        private final HashMap<Character, SuffixTreeNode> children = new HashMap<>();
        private int count = 0;
    }
}
