package com.annetv.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Channel {
    public final String id;
    public final int number;
    public final String name;
    public final String group;
    public final boolean favorite;
    public final List<String> streamUrls;

    public Channel(String id, int number, String name, String group, boolean favorite, List<String> streamUrls) {
        this.id = id;
        this.number = number;
        this.name = name;
        this.group = group;
        this.favorite = favorite;

        List<String> cleaned = new ArrayList<>();
        if (streamUrls != null) {
            for (String url : streamUrls) {
                String value = url == null ? "" : url.trim();
                if (!value.isEmpty() && !cleaned.contains(value)) cleaned.add(value);
            }
        }
        this.streamUrls = Collections.unmodifiableList(cleaned);
    }

    public boolean hasStreams() {
        return !streamUrls.isEmpty();
    }

    public String streamAt(int index) {
        if (streamUrls.isEmpty()) return "";
        int safe = Math.max(0, Math.min(index, streamUrls.size() - 1));
        return streamUrls.get(safe);
    }
}
