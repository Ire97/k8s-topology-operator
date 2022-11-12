package it.unict;

import io.fabric8.kubernetes.api.model.Node;
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

    List<String> nodeHostList =  resource.getSpec().getNodes();
    Map<String, String> nodeSelectorMap = resource.getSpec().getNodeSelector();
    List<Node> nodeList = new ArrayList();

    if(nodeHostList != null){
      log.info("Selected Nodes: ");
      for(String nodeName : nodeHostList){
        log.info("- {}", nodeName);
        nodeList.add(client.nodes().withName(nodeName).get());
      }
    }else if(nodeSelectorMap != null){
      for(String s : nodeSelectorMap.keySet()){
        nodeList = client.nodes().withLabel(s, nodeSelectorMap.get(s)).list().getItems();
        log.info("Selected Nodes by NodeSelector: ");
        for(Node n : nodeList){
          log.info("- {}", n.getMetadata().getLabels().get("kubernetes.io/hostname"));
        }
      }
    }else{
      nodeList = client.nodes().list().getItems();
      log.info("Selected Nodes by Kubernetes: ");
      for(Node n : nodeList){
        log.info("- {}", n.getMetadata().getLabels().get("kubernetes.io/hostname"));
      }
    }
    log.info("------------------------------------");

    nodeList.forEach(node -> {
      String nodeName = node.getMetadata().getLabels().get("kubernetes.io/hostname");
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