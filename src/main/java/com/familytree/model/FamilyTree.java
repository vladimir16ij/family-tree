package com.familytree.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class FamilyTree {
    private final Map<String, Person> peopleById = new LinkedHashMap<>();
    private final List<Relationship> relationships = new ArrayList<>();

    public List<Person> people() {
        return List.copyOf(peopleById.values());
    }

    public Optional<Person> find(PersonId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(peopleById.get(id.value()));
    }

    public Person addPerson(Person p) {
        peopleById.put(p.id().value(), p);
        return p;
    }

    public void removePerson(PersonId id) {
        peopleById.remove(id.value());
        relationships.removeIf(r -> r.from().equals(id) || r.to().equals(id));
    }

    public List<Relationship> relationships() {
        return List.copyOf(relationships);
    }

    public void addRelationship(Relationship r) {
        Objects.requireNonNull(r, "r");
        if (r.from().equals(r.to())) return;
        if (!peopleById.containsKey(r.from().value()) || !peopleById.containsKey(r.to().value())) return;

        Relationship normalized = normalize(r);
        if (relationships.contains(normalized)) return;

        // For partners, store one normalized edge only.
        relationships.add(normalized);
    }

    public void removeRelationship(Relationship r) {
        if (r == null) return;
        relationships.remove(normalize(r));
    }

    public List<Person> partnersOf(PersonId id) {
        return relatedPeople(id, RelationshipType.PARTNER_OF);
    }

    public List<Person> parentsOf(PersonId childId) {
        Objects.requireNonNull(childId, "childId");
        return relationships.stream()
                .filter(r -> r.type() == RelationshipType.PARENT_OF && r.to().equals(childId))
                .map(Relationship::from)
                .distinct()
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<Person> childrenOf(PersonId parentId) {
        Objects.requireNonNull(parentId, "parentId");
        return relationships.stream()
                .filter(r -> r.type() == RelationshipType.PARENT_OF && r.from().equals(parentId))
                .map(Relationship::to)
                .distinct()
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public void removeAllRelationshipsBetween(PersonId a, PersonId b, RelationshipType type) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(type, "type");
        Relationship r1 = normalize(new Relationship(a, b, type));
        Relationship r2 = normalize(new Relationship(b, a, type));
        relationships.removeIf(r -> r.equals(r1) || r.equals(r2));
    }

    private List<Person> relatedPeople(PersonId id, RelationshipType type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Set<PersonId> ids = relationships.stream()
                .filter(r -> r.type() == type && (r.from().equals(id) || r.to().equals(id)))
                .map(r -> r.from().equals(id) ? r.to() : r.from())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return ids.stream()
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Relationship normalize(Relationship r) {
        if (r.type() != RelationshipType.PARTNER_OF) return r;
        // normalize undirected partner edge to (min, max)
        String a = r.from().value();
        String b = r.to().value();
        if (a.compareTo(b) <= 0) return r;
        return new Relationship(r.to(), r.from(), r.type());
    }
}

