package berlin.yuna.nativeexample;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.nats.service.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@SuppressWarnings({
    "java:S106", // Standard outputs should not be used directly to log anything
    "java:S1192", // String literals should not be duplicated
    "java:S2259" // Null pointers should not be referenced
})
public class Main {

    public static void main(String[] args) {
        // Start the NATS server
        try (final berlin.yuna.natsserver.logic.Nats server = new berlin.yuna.natsserver.logic.Nats().start()) {

            // Connect client to the NATS server
            try (Connection nc = Nats.connect(new Options.Builder().server(server.url()).build())) {
                // endpoints can be created ahead of time
                // or created directly by the ServiceEndpoint builder.
                Endpoint epEcho = Endpoint.builder()
                    .name("EchoEndpoint")
                    .subject("echo")
                    .build();

                // Sort is going to be grouped. This will affect the actual subject
                Group sortGroup = new Group("sort");

                // 4 service endpoints. 3 in service 1, 1 in service 2
                // - We will reuse an endpoint definition, so we make it ahead of time
                // - For echo, we could have reused a handler as well, if we wanted to.
                ServiceEndpoint seEcho1 = ServiceEndpoint.builder()
                    .endpoint(epEcho)
                    .handler(msg -> handleEchoMessage(nc, msg, "S1E")) // see below: handleEchoMessage below
                    .statsDataSupplier(new ExampleStatsDataSupplier()) // see below: ExampleStatsDataSupplier
                    .build();

                ServiceEndpoint seEcho2 = ServiceEndpoint.builder()
                    .endpoint(epEcho)
                    .handler(msg -> handleEchoMessage(nc, msg, "S2E"))
                    .build();

                // you can make the Endpoint directly on the Service Endpoint Builder
                ServiceEndpoint seSort1A = ServiceEndpoint.builder()
                    .group(sortGroup)
                    .endpointName("SortEndpointAscending")
                    .endpointSubject("ascending")
                    .handler(msg -> handleSortAscending(nc, msg))
                    .build();

                // you can also make an endpoint with a constructor instead of a builder.
                Endpoint endSortD = new Endpoint("SortEndpointDescending", "descending");
                ServiceEndpoint seSort1D = ServiceEndpoint.builder()
                    .group(sortGroup)
                    .endpoint(endSortD)
                    .handler(msg -> handlerSortDescending(nc, msg))
                    .build();

                // Create the service from service endpoints.
                String serviceName1 = "Service1";
                String serviceName2 = "Service2";

                Service service1 = new ServiceBuilder()
                    .connection(nc)
                    .name(serviceName1)
                    .description("Service1 Description") // optional
                    .version("0.0.1")
                    .addServiceEndpoint(seEcho1)
                    .addServiceEndpoint(seSort1A)
                    .addServiceEndpoint(seSort1D)
                    .build();

                Service service2 = new ServiceBuilder()
                    .connection(nc)
                    .name(serviceName2)
                    .version("0.0.1")
                    .addServiceEndpoint(seEcho2) // another of the echo type
                    .build();

                System.out.println("\n" + service1);
                System.out.println("\n" + service2);

                // ----------------------------------------------------------------------------------------------------
                // Start the services
                // ----------------------------------------------------------------------------------------------------
                CompletableFuture<Boolean> serviceStoppedFuture1 = service1.startService();
                CompletableFuture<Boolean> serviceStoppedFuture2 = service2.startService();

                // ----------------------------------------------------------------------------------------------------
                // Call the services
                // ----------------------------------------------------------------------------------------------------
                System.out.println();
                String request = null;
                for (int x = 1; x <= 9; x++) { // run ping a few times to see it hit different services
                    request = randomText();
                    String subject = "echo";
                    CompletableFuture<Message> reply = nc.request(subject, request.getBytes());
                    String response = new String(reply.get().getData());
                    System.out.println(x + ". Called " + infoString(subject, request, response));
                }

                // sort subjects are formed this way because the endpoints have groups
                String subject = "sort.ascending";
                CompletableFuture<Message> reply = nc.request(subject, request.getBytes());
                String response = new String(reply.get().getData());
                System.out.println("1. Called " + infoString(subject, request, response));

                subject = "sort.descending";
                reply = nc.request(subject, request.getBytes());
                response = new String(reply.get().getData());
                System.out.println("1. Called " + infoString(subject, request, response));

                // ----------------------------------------------------------------------------------------------------
                // discovery
                // ----------------------------------------------------------------------------------------------------
                Discovery discovery = new Discovery(nc, 1000, 3);

                // ----------------------------------------------------------------------------------------------------
                // ping discover variations
                // ----------------------------------------------------------------------------------------------------
                List<PingResponse> pingResponses = discovery.ping();
                printDiscovery("Ping", "[All]", pingResponses);

                pingResponses = discovery.ping(serviceName1);
                printDiscovery("Ping", serviceName1, pingResponses);

                pingResponses = discovery.ping(serviceName2);
                printDiscovery("Ping", serviceName2, pingResponses);

                // ----------------------------------------------------------------------------------------------------
                // info discover variations
                // ----------------------------------------------------------------------------------------------------
                List<InfoResponse> infoResponses = discovery.info();
                printDiscovery("Info", "[All]", infoResponses);

                infoResponses = discovery.info(serviceName1);
                printDiscovery("Info", serviceName1, infoResponses);

                infoResponses = discovery.info(serviceName2);
                printDiscovery("Info", serviceName2, infoResponses);

                // ----------------------------------------------------------------------------------------------------
                // stats discover variations
                // ----------------------------------------------------------------------------------------------------
                List<StatsResponse> statsResponseList = discovery.stats();
                printDiscovery("Stats", "[All]", statsResponseList);

                statsResponseList = discovery.stats(serviceName1);
                printDiscovery("Stats", serviceName1, statsResponseList); // will show echo without data decoder

                statsResponseList = discovery.stats(serviceName2);
                printDiscovery("Stats", serviceName2, statsResponseList);

                // ----------------------------------------------------------------------------------------------------
                // stop the service
                // ----------------------------------------------------------------------------------------------------
                service1.stop();
                service2.stop();

                // stopping the service will complete the futures received when starting the service
                System.out.println("\nService 1 stopped ? " + serviceStoppedFuture1.get(1, TimeUnit.SECONDS));
                System.out.println("Service 2 stopped ? " + serviceStoppedFuture2.get(2, TimeUnit.SECONDS));
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String infoString(final String subject, final String request, final String response) {
        return subject + " with [" + request + "] Received " + response;
    }

    private static JsonValue replyBody(final String label, final byte[] data, final String handlerId) {
        return JsonValueUtils.mapBuilder()
            .put(label, new String(data))
            .put("hid", handlerId)
            .toJsonValue();
    }

    private static void handlerSortDescending(final Connection nc, final ServiceMessage smsg) {
        byte[] data = smsg.getData();
        Arrays.sort(data);
        int len = data.length;
        byte[] descending = new byte[len];
        for (int x = 0; x < len; x++) {
            descending[x] = data[len - x - 1];
        }
        smsg.respond(nc, replyBody("sort_descending", descending, "S1D"));
    }

    private static void handleSortAscending(final Connection nc, final ServiceMessage smsg) {
        byte[] ascending = smsg.getData();
        Arrays.sort(ascending);
        smsg.respond(nc, replyBody("sort_ascending", ascending, "S1A"));
    }

    private static void handleEchoMessage(final Connection nc, final ServiceMessage smsg, final String handlerId) {
        smsg.respond(nc, replyBody("echo", smsg.getData(), handlerId));
    }

    @SuppressWarnings("rawtypes")
    private static void printDiscovery(final String action, final String label, final List objects) {
        System.out.println("\n" + action + " " + label);
        for (Object o : objects) {
            System.out.println("  " + o);
        }
    }

    static class ExampleStatsData implements JsonSerializable {
        private final String sData;
        private final int iData;

        public ExampleStatsData(final String sData, final int iData) {
            this.sData = sData;
            this.iData = iData;
        }

        @Override
        public String toJson() {
            return toJsonValue().toJson();
        }

        @Override
        public JsonValue toJsonValue() {
            Map<String, JsonValue> map = new HashMap<>();
            map.put("sdata", new JsonValue(sData));
            map.put("idata", new JsonValue(iData));
            return new JsonValue(map);
        }

        @Override
        public String toString() {
            return toJsonValue().toString(getClass());
        }
    }

    static class ExampleStatsDataSupplier implements Supplier<JsonValue> {
        int x = 0;

        @Override
        public JsonValue get() {
            ++x;
            return new ExampleStatsData("s-" + hashCode(), x).toJsonValue();
        }
    }

    static String randomText() {
        return Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime());
    }
}

