package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.CorsFilter;
import com.modusbox.client.processor.EncodeAuthHeader;
import com.modusbox.client.processor.TokenStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class AuthRouter extends RouteBuilder {
    private final RouteExceptionHandlingConfigurer exception = new RouteExceptionHandlingConfigurer();
    private final CorsFilter corsFilter = new CorsFilter();
    private final EncodeAuthHeader encodeAuthHeader = new EncodeAuthHeader();

    private static final String ROUTE_ID_ACCESS_TOKEN = "com.modusbox.postGetAyapayAcessToken";
    private static final String ROUTE_ID_LOGIN = "com.modusbox.postAyapayLogin";

    public void configure() {

        // Add custom global exception handling strategy
        exception.configureExceptionHandling(this);

        from("direct:postGetAyapayAcessToken").routeId(ROUTE_ID_ACCESS_TOKEN)
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, " + ROUTE_ID_ACCESS_TOKEN + "', null, null, null)") // default logging
                /*
                 * BEGIN processing
                 */
                .setBody(simple("{}"))
                .removeHeaders("CamelHttp*")
                .removeHeader(Exchange.HTTP_URI)
                .removeHeader(Exchange.CONTENT_TYPE)
//                .setHeader("Content-Type", constant("application/json"))
//                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))

                .setProperty("ayapayAccessToken", method(TokenStore.class, "getAccessToken()"))

                .choice()
                    .when(method(TokenStore.class, "getAccessToken()").isEqualTo(""))
                        // Set Authorization header for basic auth mechanism
                        .setProperty("authHeader", simple("${properties:dfsp.username}:${properties:dfsp.password}"))
                        .process(encodeAuthHeader)
                        .process(exchange -> System.out.println())
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'Calling backend API, get access token', " +
                                "'Tracking the request', 'Track the response', " +
                                "'Request sent to, POST {{dfsp.host}}/token?grant_type=client_credentials')")
                        .toD("{{dfsp.host}}/token?grant_type=client_credentials&bridgeEndpoint=true")
        //                .toD("https://yordan.free.beeceptor.com/token?grant_type=client_credentials&bridgeEndpoint=true")
                        .unmarshal().json()
                        .setProperty("ayapayAccessToken", simple("${body['access_token']}"))
                        .setProperty("ayapayAccessTokenExpiration", simple("${body['expires_in']}"))
                        .bean(TokenStore.class, "setAccessToken(${exchangeProperty.ayapayAccessToken}, ${exchangeProperty.ayapayAccessTokenExpiration})")
                        .process(exchange -> System.out.println())

                        // Add CORS headers
        //				.process(corsFilter)

                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'Response from backend API, get access token', " +
                                "'Tracking the response', 'Verify the response', null)")
                .end()

                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Send response, " + ROUTE_ID_ACCESS_TOKEN + "', null, null, 'Output Payload: ${body}')") // default logging
        ;

        from("direct:postAyapayLogin").routeId(ROUTE_ID_LOGIN)
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Request received, " + ROUTE_ID_LOGIN + "', null, null, null)") // default logging
                /*
                 * BEGIN processing
                 */

                // Set headers and config
                .setBody(simple("{}"))
                .removeHeaders("CamelHttp*")
                .removeHeader(Exchange.HTTP_URI)
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                // Set Token header for auth mechanism
                .setHeader("Token", simple("Bearer ${exchangeProperty.ayapayAccessToken}"))
//.process(exchange -> System.out.println())

                .setProperty("ayapayRefreshToken", method(TokenStore.class, "getRefreshToken()"))

                .choice()
//                  .when(simple("${exchangeProperty.ayapayRefreshToken} == ''"))
//                  .when(exchangeProperty("ayapayRefreshToken").isEqualTo(""))
                    .when(method(TokenStore.class, "getRefreshToken()").isEqualTo(""))
                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                            "'Calling backend API, login', " +
                            "'Tracking the request', 'Track the response', " +
                            "'Request sent to, POST {{dfsp.host}}/user/login')")

                        // Set body
                        .marshal().json()
                        .transform(datasonnet("resource:classpath:mappings/postAyapayLoginRequest.ds"))
                        .setBody(simple("${body.content}"))
                        .marshal().json()

                        .toD("{{dfsp.host}}/{{dfsp.api-version}}/user/login?bridgeEndpoint=true")
                        .unmarshal().json()
                        .process(exchange -> System.out.println())
                        .setProperty("ayapayRefreshToken", simple("${body['data']['token']}"))
                        .setProperty("ayapayRefreshTokenExpiration", simple("${body['data']['expiredAt']}"))
                        .bean(TokenStore.class, "setRefreshToken(${exchangeProperty.ayapayRefreshToken}, ${exchangeProperty.ayapayRefreshTokenExpiration})")
                        .process(exchange -> System.out.println())
                        // Add CORS headers
        //				.process(corsFilter)

                        .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                                "'Response from backend API, login', " +
                                "'Tracking the response', 'Verify the response', null)")
                .end()

                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Send response, " + ROUTE_ID_LOGIN + "', null, null, 'Output Payload: ${body}')") // default logging
        ;

    }
}
