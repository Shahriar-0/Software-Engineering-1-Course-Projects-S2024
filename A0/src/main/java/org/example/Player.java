package org.example;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class Player {

    private String name;
    private ArrayList<Membership> memberships = new ArrayList<>();

    public Player(String name) {
        this.name = name;
    }

    public void addMembership(Membership membership) {
        for (Membership m : memberships) {
            if (m.hasOverlap(membership)) {
                throw new IllegalArgumentException("Overlapping memberships");
            }
        }
        memberships.add(membership);
    }

    public Map<String, Integer> getDaysAsMemberByTeam() {
        return memberships
            .stream()
            .collect(
                Collectors.groupingBy(
                    Membership::getTeamName,
                    Collectors.summingInt(Membership::getMembershipDays)
                )
            );
    }
}
