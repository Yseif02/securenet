package edu.yu.cs.com1320.project.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrieImplTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void put() {
    }

    @Test
    void get() {
    }

    @Test
    void getSorted() {
    }

    @Test
    void getAllWithPrefixSorted() {

    }

    @Test
    void deleteAllWithPrefix() {
    }

    @Test
    void deleteAll() {
    }

    @Test
    void delete() {
    }
}
/*
public List<Value> getAllWithPrefixSorted(String prefix, Comparator<Value> comparator) {
    List<Value> documentListToReturn = new ArrayList<>();
    TrieImpl.Node prefixNode = getNode(this.root, prefix, 0);
    assert prefixNode != null;
    documentListToReturn = this.getPrefixList(prefixNode, documentListToReturn);
    documentListToReturn.sort(comparator);
    return documentListToReturn;
}
private TrieImpl.Node getNode(TrieImpl.Node node, String key, int depth){
    if (node == null) return null;
    if(depth == key.length()) return node;
    char currentChar = key.charAt(depth);
    return this.getNode(node.links[currentChar], key, depth + 1);
}

private List<Value> getPrefixList(TrieImpl.Node node, List<Value> documentList){
    if(node == null) return documentList;
    if(!node.documents.isEmpty()) documentList.addAll(node.documents);
    if(Arrays.stream(node.links).allMatch(element -> element == null)) return documentList;
    for (TrieImpl.Node childNode : node.links){
        getPrefixList(childNode, documentList);
    }
    return documentList;
}*/
