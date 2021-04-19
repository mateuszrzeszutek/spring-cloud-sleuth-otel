package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.instrumentation.api.instrumenter.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpResponse;
import org.springframework.lang.Nullable;

public class SleuthHttpNetAttributesExtractor<RQ extends HttpRequest, RS extends HttpResponse>
		extends NetAttributesExtractor<RQ, RS> {

	@Override
	protected String transport(RQ rq) {
		return SemanticAttributes.NetTransportValues.IP_TCP.getValue();
	}

	@Override
	protected String peerName(RQ rq, RS rs) {
		return null;
	}

	// TODO: add onStart method variants that accept just the request
	// spring does not always have the request available after the response is received

	@Override
	protected Long peerPort(RQ rq, RS rs) {
		HttpRequest request = getActualHttpRequest(rq);
		if (request == null) {
			return null;
		}
		try {
			int port = request.remotePort();
			return port == 0 ? null : (long) port;
		}
		catch (NullPointerException ignored) {
			return null;
		}
	}

	@Override
	protected String peerIp(RQ rq, RS rs) {
		HttpRequest request = getActualHttpRequest(rq);
		if (request == null) {
			return null;
		}
		try {
			return request.remoteIp();
		}
		catch (NullPointerException ignored) {
			return null;
		}
	}

	private HttpRequest getActualHttpRequest(@Nullable RQ rq) {
		if (rq == null) {
			return null;
		}
		// unwrapping HttpClientBeanPostProcessor$HttpClientRequestWrapper - calling
		// remotePort() or remoteIp() directly throws NPE
		if (rq.getClass().getName().equals(
				"org.springframework.cloud.sleuth.instrument.web.client.HttpClientBeanPostProcessor$HttpClientRequestWrapper")) {
			Object actualRequest = rq.unwrap();
			if (!(actualRequest instanceof HttpRequest)) {
				return null;
			}
			return (HttpRequest) actualRequest;
		}
		return rq;
	}

}
