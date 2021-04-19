package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.regex.Pattern;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.util.StringUtils;

public class OtelHttpServerHandler implements HttpServerHandler {

	private final Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter;

	private final HttpRequestParser httpServerRequestParser;

	private final HttpResponseParser httpServerResponseParser;

	private final Pattern pattern;

	public OtelHttpServerHandler(Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter,
			HttpRequestParser httpServerRequestParser, HttpResponseParser httpServerResponseParser,
			SkipPatternProvider skipPatternProvider) {
		this.instrumenter = instrumenter;
		this.httpServerRequestParser = httpServerRequestParser;
		this.httpServerResponseParser = httpServerResponseParser;
		this.pattern = skipPatternProvider.skipPattern();
	}

	@Override
	public Span handleReceive(HttpServerRequest request) {
		String url = request.path();
		boolean shouldSkip = !StringUtils.isEmpty(url) && this.pattern.matcher(url).matches();
		if (shouldSkip) {
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}

		Context parentContext = Context.root();
		Context context = instrumenter.start(parentContext, request);
		Span span = OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.fromContext(context), context);
		parseRequest(span, request);
		return span;
	}

	private void parseRequest(Span span, HttpServerRequest request) {
		if (httpServerRequestParser != null) {
			this.httpServerRequestParser.parse(request, span.context(), span);
		}
	}

	@Override
	public void handleSend(HttpServerResponse response, Span span) {
		if (span instanceof OtelSpan) {
			parseResponse(span, response);
			// TODO: propagate context correctly
			Context context = Context.root().with(((OtelSpan) span).delegate);
			instrumenter.end(context, response.request(), response, response.error());
		}
	}

	private void parseResponse(Span span, HttpServerResponse response) {
		if (this.httpServerResponseParser != null) {
			this.httpServerResponseParser.parse(response, span.context(), span);
		}
	}

}
