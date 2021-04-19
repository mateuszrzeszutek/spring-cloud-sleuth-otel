package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.lang.Nullable;

public class OtelHttpClientHandler implements HttpClientHandler {

	private static final Log log = LogFactory.getLog(OtelHttpClientHandler.class);

	private final Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter;

	private final HttpRequestParser httpClientRequestParser;

	private final HttpResponseParser httpClientResponseParser;

	private final SamplerFunction<HttpRequest> samplerFunction;

	public OtelHttpClientHandler(Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter,
			HttpRequestParser httpClientRequestParser, HttpResponseParser httpClientResponseParser,
			SamplerFunction<HttpRequest> samplerFunction) {
		this.instrumenter = instrumenter;
		this.httpClientRequestParser = httpClientRequestParser;
		this.httpClientResponseParser = httpClientResponseParser;
		this.samplerFunction = samplerFunction;
	}

	@Override
	public Span handleSend(HttpClientRequest request) {
		return startSpan(Context.current(), request);
	}

	@Override
	public Span handleSend(HttpClientRequest request, @Nullable TraceContext parent) {
		Context parentContext;
		if (parent instanceof OtelTraceContext) {
			// TODO: propagate context correctly
			parentContext = Context.current().with(((OtelTraceContext) parent).span());
		}
		else {
			parentContext = Context.current();
		}
		return startSpan(parentContext, request);
	}

	private Span startSpan(Context parentContext, HttpClientRequest request) {
		if (Boolean.FALSE.equals(this.samplerFunction.trySample(request))) {
			if (log.isDebugEnabled()) {
				log.debug("Returning an invalid span since url [" + request.path() + "] is on a list of urls to skip");
			}
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}
		if (!instrumenter.shouldStart(parentContext, request)) {
			log.debug("Returning an invalid span because there already is a CLIENT span in the context");
			return OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.getInvalid());
		}

		Context context = instrumenter.start(parentContext, request);
		Span span = OtelSpan.fromOtel(io.opentelemetry.api.trace.Span.fromContext(context), context);
		parseRequest(span, request);
		return span;
	}

	private void parseRequest(Span span, HttpClientRequest request) {
		if (httpClientRequestParser != null) {
			httpClientRequestParser.parse(request, span.context(), span);
		}
	}

	@Override
	public void handleReceive(HttpClientResponse response, Span span) {
		if (span instanceof OtelSpan) {
			parseResponse(span, response);
			// TODO: propagate context correctly
			Context context = Context.root().with(((OtelSpan) span).delegate);
			instrumenter.end(context, response.request(), response, response.error());
		}
	}

	private void parseResponse(Span span, HttpClientResponse response) {
		if (httpClientResponseParser != null) {
			httpClientResponseParser.parse(response, span.context(), span);
		}
	}

}
