package io.github.andrispowq.lostrailways.planner;

public record NetworkNode(String id, Kind kind, RoutePoint position) {
    public enum Kind { STATION, JUNCTION, BOUNDARY }
}
