package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.cloud.sleuth.http.HttpServerRequest;

public class SleuthHttpHeaderGetter implements TextMapGetter<HttpServerRequest> {

	@Override
	public Iterable<String> keys(HttpServerRequest request) {
		return request.headerNames();
	}

	@Override
	public String get(HttpServerRequest request, String name) {
		return request.header(name);
	}

}
