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

package org.springframework.cloud.sleuth.autoconfig.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.SamplerFunction;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.SleuthBaggageProperties;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.ConditionalOnSleuthWeb;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.SleuthWebProperties;
import org.springframework.cloud.sleuth.http.HttpClientHandler;
import org.springframework.cloud.sleuth.http.HttpClientRequest;
import org.springframework.cloud.sleuth.http.HttpClientResponse;
import org.springframework.cloud.sleuth.http.HttpRequest;
import org.springframework.cloud.sleuth.http.HttpRequestParser;
import org.springframework.cloud.sleuth.http.HttpResponseParser;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientSampler;
import org.springframework.cloud.sleuth.instrument.web.HttpServerRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.cloud.sleuth.instrument.web.SkipPatternProvider;
import org.springframework.cloud.sleuth.otel.bridge.EventPublishingContextWrapper;
import org.springframework.cloud.sleuth.otel.bridge.OtelBaggageManager;
import org.springframework.cloud.sleuth.otel.bridge.OtelCurrentTraceContext;
import org.springframework.cloud.sleuth.otel.bridge.OtelHttpClientHandler;
import org.springframework.cloud.sleuth.otel.bridge.OtelHttpServerHandler;
import org.springframework.cloud.sleuth.otel.bridge.OtelPropagator;
import org.springframework.cloud.sleuth.otel.bridge.OtelSpanCustomizer;
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer;
import org.springframework.cloud.sleuth.otel.bridge.SkipPatternSampler;
import org.springframework.cloud.sleuth.otel.bridge.SleuthHttpAttributesExtractor;
import org.springframework.cloud.sleuth.otel.bridge.SleuthHttpHeaderGetter;
import org.springframework.cloud.sleuth.otel.bridge.SleuthHttpHeaderSetter;
import org.springframework.cloud.sleuth.otel.bridge.SleuthHttpNetAttributesExtractor;
import org.springframework.cloud.sleuth.otel.bridge.SleuthHttpPathAttributeExtractor;
import org.springframework.cloud.sleuth.otel.bridge.SpanExporterCustomizer;
import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable the bridge between Sleuth API and OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenTelemetry.class)
@Import({ OtelLogConfiguration.class, OtelExporterConfiguration.class })
class OtelBridgeConfiguration {

	@Bean
	Tracer otelTracerBridge(io.opentelemetry.api.trace.Tracer tracer, ApplicationEventPublisher publisher,
			CurrentTraceContext currentTraceContext, SleuthBaggageProperties sleuthBaggageProperties) {
		return new OtelTracer(tracer, publisher, new OtelBaggageManager(currentTraceContext,
				sleuthBaggageProperties.getRemoteFields(), sleuthBaggageProperties.getTagFields(), publisher));
	}

	// Both CurrentTraceContext & application of a ContextStorage wrapper
	@Bean
	@ConditionalOnMissingBean
	OtelCurrentTraceContext otelCurrentTraceContext(ApplicationEventPublisher publisher) {
		ContextStorage.addWrapper(new EventPublishingContextWrapper(publisher));
		return new OtelCurrentTraceContext();
	}

	@Bean
	SpanCustomizer otelSpanCustomizer() {
		return new OtelSpanCustomizer();
	}

	@Bean
	Propagator otelPropagator(ContextPropagators contextPropagators, io.opentelemetry.api.trace.Tracer tracer) {
		return new OtelPropagator(contextPropagators, tracer);
	}

	@Bean
	@ConditionalOnMissingBean
	SpanExporterCustomizer noOpSleuthSpanFilterConverter() {
		return new SpanExporterCustomizer() {

		};
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnSleuthWeb
	@EnableConfigurationProperties({ SleuthWebProperties.class, OtelProperties.class })
	static class TraceOtelHttpBridgeConfiguration {

		@Bean
		Instrumenter<HttpClientRequest, HttpClientResponse> otelHttpClientInstrumenter(OpenTelemetry openTelemetry,
				OtelProperties otelProperties) {
			HttpAttributesExtractor<HttpClientRequest, HttpClientResponse> httpAttributesExtractor = new SleuthHttpAttributesExtractor<>();
			SpanNameExtractor<HttpClientRequest> spanNameExtractor = SpanNameExtractor.http(httpAttributesExtractor);
			return Instrumenter
					.<HttpClientRequest, HttpClientResponse>newBuilder(openTelemetry,
							otelProperties.getInstrumentationName(), spanNameExtractor)
					.addAttributesExtractor(httpAttributesExtractor)
					.addAttributesExtractor(new SleuthHttpNetAttributesExtractor<>())
					.addAttributesExtractor(new SleuthHttpPathAttributeExtractor<>())
					.newClientInstrumenter(new SleuthHttpHeaderSetter());
		}

		@Bean
		Instrumenter<HttpServerRequest, HttpServerResponse> otelHttpServerInstrumenter(OpenTelemetry openTelemetry,
				OtelProperties otelProperties) {
			HttpAttributesExtractor<HttpServerRequest, HttpServerResponse> httpAttributesExtractor = new SleuthHttpAttributesExtractor<>();
			SpanNameExtractor<HttpServerRequest> spanNameExtractor = SpanNameExtractor.http(httpAttributesExtractor);
			return Instrumenter
					.<HttpServerRequest, HttpServerResponse>newBuilder(openTelemetry,
							otelProperties.getInstrumentationName(), spanNameExtractor)
					.addAttributesExtractor(httpAttributesExtractor)
					.addAttributesExtractor(new SleuthHttpNetAttributesExtractor<>())
					.addAttributesExtractor(new SleuthHttpPathAttributeExtractor<>())
					.newServerInstrumenter(new SleuthHttpHeaderGetter());
		}

		@Bean
		HttpClientHandler otelHttpClientHandler(
				@Qualifier("otelHttpClientInstrumenter") Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter,
				@Nullable @HttpClientRequestParser HttpRequestParser httpClientRequestParser,
				@Nullable @HttpClientResponseParser HttpResponseParser httpClientResponseParser,
				SamplerFunction<HttpRequest> samplerFunction) {
			return new OtelHttpClientHandler(instrumenter, httpClientRequestParser, httpClientResponseParser,
					samplerFunction);
		}

		@Bean
		HttpServerHandler otelHttpServerHandler(
				@Qualifier("otelHttpServerInstrumenter") Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter,
				@Nullable @HttpServerRequestParser HttpRequestParser httpServerRequestParser,
				@Nullable @HttpServerResponseParser HttpResponseParser httpServerResponseParser,
				ObjectProvider<SkipPatternProvider> skipPatternProvider) {
			return new OtelHttpServerHandler(instrumenter, httpServerRequestParser, httpServerResponseParser,
					skipPatternProvider.getIfAvailable(() -> () -> Pattern.compile("")));
		}

		@Bean
		@ConditionalOnMissingBean(name = HttpClientSampler.NAME)
		SamplerFunction<HttpRequest> defaultHttpClientSampler(SleuthWebProperties sleuthWebProperties) {
			String skipPattern = sleuthWebProperties.getClient().getSkipPattern();
			if (skipPattern == null) {
				return SamplerFunction.deferDecision();
			}
			return new SkipPatternSampler(Pattern.compile(skipPattern));
		}

	}

}
