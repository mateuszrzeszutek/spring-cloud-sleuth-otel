package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.instrumentation.api.instrumenter.HttpAttributesExtractor;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpResponse;

public class SleuthHttpAttributesExtractor<RQ extends HttpRequest, RS extends HttpResponse>
		extends HttpAttributesExtractor<RQ, RS> {

	@Override
	protected String method(RQ rq) {
		return rq.method();
	}

	@Override
	protected String url(RQ rq) {
		return rq.url();
	}

	@Override
	protected String target(RQ rq) {
		return null;
	}

	@Override
	protected String host(RQ rq) {
		return null;
	}

	@Override
	protected String route(RQ rq) {
		return rq.route();
	}

	@Override
	protected String scheme(RQ rq) {
		return null;
	}

	@Override
	protected String userAgent(RQ rq) {
		return rq.header("User-Agent");
	}

	@Override
	protected Long requestContentLength(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected Long requestContentLengthUncompressed(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected Long statusCode(RQ rq, RS rs) {
		return (long) rs.statusCode();
	}

	@Override
	protected String flavor(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected Long responseContentLength(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected Long responseContentLengthUncompressed(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected String serverName(RQ rq, RS rs) {
		return null;
	}

	@Override
	protected String clientIp(RQ rq, RS rs) {
		return null;
	}

}
