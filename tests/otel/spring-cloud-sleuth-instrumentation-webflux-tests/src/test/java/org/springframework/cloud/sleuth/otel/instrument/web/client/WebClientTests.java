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

package org.springframework.cloud.sleuth.otel.instrument.web.client;

import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.otel.OtelTestSpanHandler;
import org.springframework.cloud.sleuth.otel.bridge.ArrayListSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = WebClientTests.Config.class)
public class WebClientTests
		extends org.springframework.cloud.sleuth.instrument.web.client.integration.sampled.WebClientTests {

	@Override
	protected String pathKey() {
		return "http.route";
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		OtelTestSpanHandler testSpanHandlerSupplier() {
			return new OtelTestSpanHandler(new ArrayListSpanProcessor());
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.alwaysOn();
		}

	}

}
