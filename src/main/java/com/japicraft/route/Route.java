package com.japicraft.route;

import java.util.*;

/**
 * A directed waypoint graph representing all enemy paths and intersections in the facility.
 * Easy to extend: register nodes with {@link #addNode(PathNode)} and link them with {@link #connect(String, String...)}.
 */
public final class Route {
    private final Map<String, PathNode> nodesById = new LinkedHashMap<>();
    private final Map<String, List<PathNode>> forwardEdges = new HashMap<>();
    private final Map<String, List<PathNode>> backwardEdges = new HashMap<>();

    public Route addNode(PathNode node) {
        nodesById.put(node.id(), node);
        forwardEdges.putIfAbsent(node.id(), new ArrayList<>());
        backwardEdges.putIfAbsent(node.id(), new ArrayList<>());
        return this;
    }

    /**
     * Connects {@code fromId} forward toward one or more {@code toIds}.
     * Also automatically registers the reverse edge for retreating when warded.
     */
    public Route connect(String fromId, String... toIds) {
        PathNode from = requireNode(fromId);
        List<PathNode> forward = forwardEdges.computeIfAbsent(fromId, _ -> new ArrayList<>());
        for (String toId : toIds) {
            PathNode to = requireNode(toId);
            if (!forward.contains(to)) {
                forward.add(to);
            }
            List<PathNode> backward = backwardEdges.computeIfAbsent(toId, _ -> new ArrayList<>());
            if (!backward.contains(from)) {
                backward.add(from);
            }
        }
        return this;
    }

    public PathNode requireNode(String id) {
        PathNode node = nodesById.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown PathNode id: " + id);
        }
        return node;
    }

    public List<PathNode> nextNodes(PathNode current) {
        return Collections.unmodifiableList(forwardEdges.getOrDefault(current.id(), List.of()));
    }

    public List<PathNode> previousNodes(PathNode current) {
        return Collections.unmodifiableList(backwardEdges.getOrDefault(current.id(), List.of()));
    }

    public Collection<PathNode> allNodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }
}
