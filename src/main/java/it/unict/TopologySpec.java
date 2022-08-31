package it.unict;

import java.util.List;

public class TopologySpec {

    // Add Spec information here
    private List<String> nodes;

    private Integer rescheduleInterval;

    public List<String> getNodes() {
        return nodes;
    }

    public Integer getRunInterval() {
        return rescheduleInterval;
    }
}
