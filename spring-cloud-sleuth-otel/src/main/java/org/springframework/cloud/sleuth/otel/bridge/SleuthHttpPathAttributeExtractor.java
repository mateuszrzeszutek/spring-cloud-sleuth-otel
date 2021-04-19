package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpResponse;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;

public class SleuthHttpPathAttributeExtractor<RQ extends HttpRequest, RS extends HttpResponse> extends AttributesExtractor<RQ, RS> {

	// TODO: is http.path some sleuth convention? can we use OTel http.route instead everywhere?
	private static final AttributeKey<String> HTTP_PATH = AttributeKey.stringKey("http.path");

	@Override
	protected void onStart(AttributesBuilder attributes, RQ request) {
		set(attributes, HTTP_PATH, request.path());
	}

	@Override
	protected void onEnd(AttributesBuilder attributes, RQ request, RS response) {
	}

}
