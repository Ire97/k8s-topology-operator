package it.unict.telemetry;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.Map;

@RegisterRestClient(configKey = "telemetry-api")
public interface TelemetryService {

    @GET
    @Path("/metrics/node/avg-latencies")
    Uni<Map<String,Float>> getAvgNodeLatencies(@QueryParam("node") String node);

    @GET
    @Path("/metrics/node/avg-latencies")
    Uni<Map<String,Map<String,Float>>> getAllAvgNodeLatencies();
}
