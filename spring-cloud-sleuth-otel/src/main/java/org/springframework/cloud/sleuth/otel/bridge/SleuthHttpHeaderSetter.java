package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.cloud.sleuth.http.HttpClientRequest;

public class SleuthHttpHeaderSetter implements TextMapSetter<HttpClientRequest> {

	@Override
	public void set(HttpClientRequest request, String name, String value) {
		request.header(name, value);
	}

}
