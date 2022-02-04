package dash.handler.definition;

import io.netty.handler.codec.http.FullHttpRequest;

import java.nio.charset.StandardCharsets;

public class HttpRequest {

    private final FullHttpRequest request;

    public HttpRequest(final FullHttpRequest request) {
        this.request = request;
    }

    public String body() {
        return request.content().toString(StandardCharsets.UTF_8);
    }
}