package com.securenet.eventprocessing.raft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Append-only Raft log that stores {@link LogEntry} objects.
 *
 * <p>The log is 1-indexed (first entry is at index 1). Index 0 is a
 * sentinel representing the empty state before any entries exist
 * (term 0, index 0).
 *
 * <p>Key Raft invariants maintained by this class:
 * <ul>
 *   <li>Entries are never reordered — only appended or truncated from the tail.</li>
 *   <li>If two logs contain an entry with the same index and term, all
 *       preceding entries are identical (Log Matching Property).</li>
 *   <li>Truncation only happens during AppendEntries conflict resolution
 *       when a follower discovers its log diverges from the leader's.</li>
 * </ul>
 *
 * <p>Thread safety: all methods are synchronized. In production the lock
 * granularity would be finer, but for correctness-first Raft this is
 * sufficient.
 */
public class RaftLog {

    private final List<LogEntry> entries = new ArrayList<>();

    /** Returns the index of the last entry, or 0 if the log is empty. */
    public synchronized long getLastIndex() {
        return entries.size();
    }

    /** Returns the term of the last entry, or 0 if the log is empty. */
    public synchronized long getLastTerm() {
        if (entries.isEmpty()) return 0;
        return entries.get(entries.size() - 1).term();
    }

    /**
     * Returns the entry at the given 1-based index, or {@code null} if
     * the index is out of range.
     */
    public synchronized LogEntry getEntry(long index) {
        if (index < 1 || index > entries.size()) return null;
        return entries.get((int) (index - 1));
    }

    /**
     * Returns the term of the entry at the given index, or 0 if the
     * index is out of range (which represents the sentinel "no entry").
     */
    public synchronized long getTermAt(long index) {
        if (index < 1 || index > entries.size()) return 0;
        return entries.get((int) (index - 1)).term();
    }

    /**
     * Appends an entry to the end of the log. The entry's index must
     * equal {@code getLastIndex() + 1}.
     *
     * @param entry the entry to append
     * @throws IllegalArgumentException if the entry's index is not
     *         sequential
     */
    public synchronized void append(LogEntry entry) {
        long expectedIndex = entries.size() + 1;
        if (entry.index() != expectedIndex) {
            throw new IllegalArgumentException(
                    "Expected index " + expectedIndex + " but got " + entry.index());
        }
        entries.add(entry);
    }

    /**
     * Returns all entries from {@code startIndex} (inclusive) to the end
     * of the log. Used by the leader to build AppendEntries batches.
     *
     * @param startIndex 1-based start index (inclusive)
     * @return unmodifiable list of entries; empty if startIndex is beyond
     *         the log
     */
    public synchronized List<LogEntry> getEntriesFrom(long startIndex) {
        if (startIndex < 1 || startIndex > entries.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(
                new ArrayList<>(entries.subList((int) (startIndex - 1), entries.size()))
        );
    }

    /**
     * Truncates the log from {@code fromIndex} (inclusive) onward.
     *
     * <p>Called during AppendEntries conflict resolution when a follower
     * discovers that its log diverges from the leader's at this index.
     * All entries from {@code fromIndex} to the end are removed.
     *
     * @param fromIndex 1-based index to truncate from (inclusive)
     */
    public synchronized void truncateFrom(long fromIndex) {
        if (fromIndex < 1 || fromIndex > entries.size()) return;
        entries.subList((int) (fromIndex - 1), entries.size()).clear();
    }

    /** Returns the total number of entries in the log. */
    public synchronized int size() {
        return entries.size();
    }
}
