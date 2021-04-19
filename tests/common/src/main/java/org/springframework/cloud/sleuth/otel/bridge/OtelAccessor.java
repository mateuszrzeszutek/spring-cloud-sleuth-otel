/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.otel.bridge;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

public final class OtelAccessor {

	private static final String INSTRUMENTATION_NAME = "org.springframework.cloud.sleuth";

	private OtelAccessor() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static Tracer tracer(OpenTelemetry openTelemetry, CurrentTraceContext currentTraceContext,
			SleuthBaggageProperties sleuthBaggageProperties, ApplicationEventPublisher publisher) {
		return new OtelTracer(otelTracer(openTelemetry), publisher, new OtelBaggageManager(currentTraceContext,
				sleuthBaggageProperties.getRemoteFields(), sleuthBaggageProperties.getTagFields(), publisher));
	}

	private static io.opentelemetry.api.trace.Tracer otelTracer(OpenTelemetry openTelemetry) {
		return openTelemetry.getTracer(INSTRUMENTATION_NAME);
	}

	public static CurrentTraceContext currentTraceContext() {
		return new OtelCurrentTraceContext();
	}

	public static TraceContext traceContext(SpanContext spanContext) {
		return OtelTraceContext.fromOtel(spanContext);
	}

	public static Propagator propagator(ContextPropagators propagators, OpenTelemetry openTelemetry) {
		return new OtelPropagator(propagators, otelTracer(openTelemetry));
	}

	public static HttpClientHandler httpClientHandler(io.opentelemetry.api.OpenTelemetry openTelemetry,
			@Nullable HttpRequestParser httpClientRequestParser, @Nullable HttpResponseParser httpClientResponseParser,
			SamplerFunction<HttpRequest> samplerFunction) {

		HttpAttributesExtractor<HttpClientRequest, HttpClientResponse> httpAttributesExtractor = new SleuthHttpAttributesExtractor<>();
		SpanNameExtractor<HttpClientRequest> spanNameExtractor = SpanNameExtractor.http(httpAttributesExtractor);
		Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter = Instrumenter
				.<HttpClientRequest, HttpClientResponse>newBuilder(openTelemetry, INSTRUMENTATION_NAME,
						spanNameExtractor)
				.addAttributesExtractor(httpAttributesExtractor)
				.addAttributesExtractor(new SleuthHttpNetAttributesExtractor<>())
				.addAttributesExtractor(new SleuthHttpPathAttributeExtractor<>())
				.newClientInstrumenter(new SleuthHttpHeaderSetter());

		return new OtelHttpClientHandler(instrumenter, httpClientRequestParser, httpClientResponseParser,
				samplerFunction);
	}

	public static HttpServerHandler httpServerHandler(io.opentelemetry.api.OpenTelemetry openTelemetry,
			HttpRequestParser httpServerRequestParser, HttpResponseParser httpServerResponseParser,
			SkipPatternProvider skipPatternProvider) {

		HttpAttributesExtractor<HttpServerRequest, HttpServerResponse> httpAttributesExtractor = new SleuthHttpAttributesExtractor<>();
		SpanNameExtractor<HttpServerRequest> spanNameExtractor = SpanNameExtractor.http(httpAttributesExtractor);
		Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter = Instrumenter
				.<HttpServerRequest, HttpServerResponse>newBuilder(openTelemetry, INSTRUMENTATION_NAME,
						spanNameExtractor)
				.addAttributesExtractor(httpAttributesExtractor)
				.addAttributesExtractor(new SleuthHttpNetAttributesExtractor<>())
				.addAttributesExtractor(new SleuthHttpPathAttributeExtractor<>())
				.newServerInstrumenter(new SleuthHttpHeaderGetter());

		return new OtelHttpServerHandler(instrumenter, httpServerRequestParser, httpServerResponseParser,
				skipPatternProvider);
	}

	public static FinishedSpan finishedSpan(SpanData spanData) {
		return new OtelFinishedSpan(spanData);
	}

}
