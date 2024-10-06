package edu.yu.introtoalgs;

import java.util.HashMap;
import java.util.HashSet;

public class TerroristNames extends TerroristNamesBase{
    //key = substring           value = all names this string belongs to
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
        if ((id == null) || id.isBlank() || id.contains(" ") || id.length() > 9 || this.allWords.contains(id)) throw new IllegalArgumentException();
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
            //currentNode.setSubstring(suffix);
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
        //given a string to search return # of times string appears as a substring in terrorist names

        //search hashset of substrings if there is a set of names pertaining to this string id
        //if yes return set size
        if ((id == null) || id.isBlank() || id.contains(" ")) throw new IllegalArgumentException();
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
        private final HashSet<String> substringWords = new HashSet<>();
        private int count = 0;
        private String substring = null;

        public String getSubstring() {
            return substring;
        }

        public void setSubstring(String substring) {
            this.substring = substring;
        }

        public HashSet<String> getSubstringWords(){
            return this.substringWords;
        }
    }


}
