package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.json.JSONException;

public class TransfersRouter extends RouteBuilder {

    private final RouteExceptionHandlingConfigurer exception = new RouteExceptionHandlingConfigurer();

    private static final String ROUTE_ID = "com.modusbox.postTransfers";
    private static final String ROUTE_ID_PUT = "com.modusbox.putTransfersByTransferId";
    private static final String COUNTER_NAME = "counter_post_transfers_requests";
    private static final String COUNTER_NAME_PUT = "counter_put_transfers_requests";
    private static final String TIMER_NAME = "histogram_post_transfers_timer";
    private static final String TIMER_NAME_PUT = "histogram_put_transfers_timer";
    private static final String HISTOGRAM_NAME = "histogram_post_transfers_requests_latency";
    private static final String HISTOGRAM_NAME_PUT = "histogram_put_transfers_requests_latency";

    public static final Counter requestCounter = Counter.build()
            .name(COUNTER_NAME)
            .help("Total requests for POST /transfers.")
            .register();

    private static final Histogram requestLatency = Histogram.build()
            .name(HISTOGRAM_NAME)
            .help("Request latency in seconds for POST /transfers.")
            .register();

    public static final Counter requestCounterPut = Counter.build()
            .name(COUNTER_NAME_PUT)
            .help("Total requests for PUT /transfers/{transferId}.")
            .register();

    private static final Histogram requestLatencyPut = Histogram.build()
            .name(HISTOGRAM_NAME_PUT)
            .help("Request latency in seconds for PUT /transfers/{transferId}.")
            .register();

    public void configure() {

        // Add custom global exception handling strategy
        exception.configureExceptionHandling(this);

        from("direct:postTransfers").routeId(ROUTE_ID).doTry()
                .process(exchange -> {
                    requestCounter.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME, requestLatency.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, " + ROUTE_ID + "', null, null, 'Input Payload: ${body}')") // default logging
                /*
                 * BEGIN processing
                 */
//.process(exchange -> System.out.println())
                .setProperty("origPayload", simple("${body}"))
                .removeHeaders("CamelHttp*")

                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(constant("{\"homeTransactionId\": \"1234\"}"))

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Response from backend API, postTransfers: ${body}', " +
                        "'Tracking the response', 'Verify the response', null)")
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Send response, " + ROUTE_ID + "', null, null, 'Output Payload: ${body}')") // default logging
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

        from("direct:putTransfersByTransferId").routeId(ROUTE_ID_PUT).doTry()
                .process(exchange -> {
                    requestCounterPut.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_PUT, requestLatencyPut.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, PUT /transfers/${header.transferId}', " +
                        "null, null, null)")
                /*
                 * BEGIN processing
                 */
//.process(exchange -> System.out.println())
                .setProperty("origPayload", simple("${body}"))

                .to("direct:postGetAyapayAcessToken")
                .to("direct:postAyapayLogin")
//.process(exchange -> System.out.println())
                .setHeader("Token", simple("Bearer ${exchangeProperty.ayapayAccessToken}"))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty.ayapayRefreshToken}"))

                .setBody(simple("${exchangeProperty.origPayload}"))
                .removeHeaders("CamelHttp*")
                .removeHeader(Exchange.HTTP_URI)
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
//.process(exchange -> System.out.println())
                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/ayapayRequestTransaction.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling backend API, postTransfers, POST {{dfsp.host}}/{{dfsp.api-version}}/transaction/requestTransaction', " +
                        "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
                // POST the transaction
//.process(exchange -> System.out.println())
                .toD("{{dfsp.host}}/{{dfsp.api-version}}/transaction/requestTransaction?bridgeEndpoint=true")
                .unmarshal().json()
//.process(exchange -> System.out.println())

                .choice()
                    .when(simple("${body['err']} != 200"))
                        .to("direct:catchCBSError")
                .endDoTry()

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Response from backend API, requestTransaction: ${body}', " +
                        "'Tracking the response', 'Verify the response', null)")

                // Verify it got reflected in CBS
                .removeHeaders("CamelHttp*")
                .removeHeader(Exchange.HTTP_URI)
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))

                .setHeader("Token", simple("Bearer ${exchangeProperty.ayapayAccessToken}"))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty.ayapayRefreshToken}"))

                .marshal().json()
                .transform(datasonnet("{\"transRefId\": payload.data.transRefId, \"messageType\": \"FO\"}"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling backend API, postTransfers, POST {{dfsp.host}}/{{dfsp.api-version}}/transaction/verifyTransaction', " +
                        "'Tracking the request', 'Track the response', 'Input Payload: ${body}')")
                .toD("{{dfsp.host}}/{{dfsp.api-version}}/transaction/verifyTransaction?bridgeEndpoint=true")
                .unmarshal().json()
//.process(exchange -> System.out.println())

                .choice()
                    .when(simple("${body['err']} != 200"))
                        .to("direct:catchCBSError")
                .endDoTry()

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/postTransfersResponse.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Final Response: ${body}', " +
                        "null, null, 'Response of PUT /transfers/${header.transferId} API')")
                .doCatch(CCCustomException.class, HttpOperationFailedException.class, JSONException.class)
                    .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_PUT)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

    }
}
