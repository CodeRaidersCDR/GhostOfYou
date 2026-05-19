package com.coderaiderscdr.ghostofyou.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActionEventLog {

    private final List<ActionEvent> events = new ArrayList<>();

    public int append(ActionEvent event) {
        events.add(event);
        return events.size();
    }

    public ActionEvent get(int id) {
        if (id <= 0 || id > events.size()) return null;
        return events.get(id - 1);
    }

    public int size() { return events.size(); }

    public List<ActionEvent> getAll() {
        return Collections.unmodifiableList(events);
    }

    public void clear() { events.clear(); }
}
