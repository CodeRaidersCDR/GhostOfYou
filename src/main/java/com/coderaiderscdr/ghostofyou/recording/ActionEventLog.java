package com.coderaiderscdr.ghostofyou.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Append-only log of {@link ActionEvent} objects, indexed by 1-based ID.
 * ID 0 means "no event" (used in frame data).
 */
public class ActionEventLog {

    private final List<ActionEvent> events = new ArrayList<>();

    /**
     * Append an event and return its 1-based ID for storage in a frame.
     *
     * @param event the event to append
     * @return 1-based index that can be written to {@link Frame#write} as {@code actionEventId}
     */
    public int append(ActionEvent event) {
        events.add(event);
        return events.size(); // 1-based
    }

    /**
     * Retrieve an event by its 1-based ID.
     *
     * @param id 1-based event ID as stored in frame data; 0 means none
     * @return the event, or {@code null} if {@code id} is 0 or out of range
     */
    public ActionEvent get(int id) {
        if (id <= 0 || id > events.size()) return null;
        return events.get(id - 1);
    }

    /** Number of events recorded so far. */
    public int size() { return events.size(); }

    /** Unmodifiable view of all events (oldest first). */
    public List<ActionEvent> getAll() {
        return Collections.unmodifiableList(events);
    }

    /** Clear all events (used when rebuilding the recorder buffer). */
    public void clear() { events.clear(); }
}
