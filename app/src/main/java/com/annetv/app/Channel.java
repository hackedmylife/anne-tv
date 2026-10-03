package com.annetv.app;

public final class Channel {
    public final String id;
    public final int number;
    public final String name;
    public final String group;
    public final boolean favorite;
    public final String streamUrl;

    public Channel(String id, int number, String name, String group, boolean favorite, String streamUrl) {
        this.id = id;
        this.number = number;
        this.name = name;
        this.group = group;
        this.favorite = favorite;
        this.streamUrl = streamUrl == null ? "" : streamUrl.trim();
    }
}
