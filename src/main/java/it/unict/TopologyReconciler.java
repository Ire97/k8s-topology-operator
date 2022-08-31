package it.unict;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import it.unict.telemetry.TelemetryService;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TopologyReconciler implements Reconciler<Topology> {
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

    resource.getSpec().getNodes().forEach(nodeName -> {
      Node node = client.nodes().withName(nodeName).get();

      if (latencyValues.containsKey(nodeName)) {
        latencyValues.get(nodeName).forEach((key, value) -> node.getMetadata().getLabels().put(
                "network.cost." + key,
                String.valueOf((oldRange == 0) ? minCost : ((value.intValue() - finalLowest) * newRange / oldRange) + minCost)
        ));
      }

      node.getMetadata().getLabels().put(
              "network.cost." + nodeName,
              String.valueOf(minCost)
      );

      client.nodes().withName(nodeName).patch(node);
    });

    return UpdateControl.<Topology>noUpdate().rescheduleAfter(resource.getSpec().getRunInterval(), TimeUnit.SECONDS);
  }
}

