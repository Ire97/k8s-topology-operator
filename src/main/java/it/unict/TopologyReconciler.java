package it.unict;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.unict.telemetry.TelemetryService;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TopologyReconciler implements Reconciler<Topology> {

  private static final Logger log = LoggerFactory.getLogger(TopologyReconciler.class);
  private final KubernetesClient client;

  @RestClient
  TelemetryService telemetryService;

  protected final int minCost = 1;

  protected final int maxCost = 100;

  public TopologyReconciler(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public UpdateControl<Topology> reconcile(Topology resource, Context context) {
    List<String> nodeNameList =  resource.getSpec().getNodes();
    Map<String, String> nodeSelectorMap = resource.getSpec().getNodeSelector();
    List<Node> nodeList = new ArrayList<>();

    if(nodeNameList != null){
      nodeList = nodeNameList.stream().map(n-> client.nodes().withName(n).get()).collect(Collectors.toList());
    }else{
      nodeList = client.nodes().withLabelSelector(new LabelSelectorBuilder().withMatchLabels(nodeSelectorMap).build()).list().getItems();
      nodeNameList = nodeList.stream().map(n-> n.getMetadata().getName()).collect(Collectors.toList());
    }

    int highest = 0;
    int lowest = 0;

    Map<String,Map<String,Float>> latencyValues = telemetryService.getAllAvgNodeLatencies().await().indefinitely();
    if (!latencyValues.isEmpty()) {
      highest = Collections.max(
              latencyValues
                      .values()
                      .stream()
                      .map(x -> Collections.max(x.values()))
                      .collect(Collectors.toList())
      ).intValue();
      lowest = Collections.min(
              latencyValues
                      .values()
                      .stream()
                      .map(x -> Collections.min(x.values()))
                      .collect(Collectors.toList())
      ).intValue();
    }

    int newRange = maxCost - minCost;
    int finalLowest = lowest;
    int finalHighest = highest;
    int oldRange = finalHighest - finalLowest;

    nodeList.forEach(node -> {
      String nodeName = node.getMetadata().getName();
      if (latencyValues.containsKey(nodeName)) {
        latencyValues.get(nodeName).forEach((key, value) -> {
          int networkCost = (oldRange == 0) ? minCost : ((value.intValue() - finalLowest) * newRange / oldRange) + minCost;
          log.info("Network cost between nodes {} and {}: {}", nodeName, key, networkCost);

          node.getMetadata().getLabels().put(
                "network.cost." + key,
                String.valueOf(networkCost)
          );
        });
      }

      node.getMetadata().getLabels().put(
              "network.cost." + nodeName,
              String.valueOf(minCost)
      );

      client.nodes().withName(nodeName).patch(node);
    });

    log.info("------------------------------------");

    return UpdateControl.<Topology>noUpdate().rescheduleAfter(resource.getSpec().getRunInterval(), TimeUnit.SECONDS);
  }
}