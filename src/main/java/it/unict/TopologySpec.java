package it.unict;

import java.util.List;
import java.util.Map;

public class TopologySpec {

    // Add Spec information here
    private List<String> nodes;

    private Integer runInterval;

    private Map<String, String> nodeSelector;

    public List<String> getNodes() {
        return nodes;
    }

    public Integer getRunInterval() {
        return runInterval;
    }

    public Map<String, String> getNodeSelector(){
        return nodeSelector;
    }
}