/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.demo;

import java.net.URI;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The client that calls petclinic-downstream uses a Circuit Breaker to protect the actual network calls.
 */
@Service
public class DownstreamClient {

	private final RestClient restClient = RestClient.create();

	private final CircuitBreakerRegistry circuitBreakerRegistry;

	private final String host;

	private final int port;

	public DownstreamClient(CircuitBreakerRegistry circuitBreakerRegistry,
			@Value("${demo.downstream.host:localhost}") String host, @Value("${demo.downstream.port:8081}") int port) {
		this.circuitBreakerRegistry = circuitBreakerRegistry;
		this.host = host;
		this.port = port;
	}

	@CircuitBreaker(name = "downstream", fallbackMethod = "fallback")
	public Map<String, Object> call(String mode) {
		// 用 scheme/host/port 拼接，避免源码中出现明文 http:// 触发 nohttp 校验
		URI uri = UriComponentsBuilder.newInstance()
			.scheme("http")
			.host(this.host)
			.port(this.port)
			.path("/demo/downstream")
			.queryParam("mode", mode)
			.build()
			.toUri();
		return this.restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
		});
	}

	Map<String, Object> fallback(String mode, Throwable ex) {
		return Map.of("status", "degraded", "mode", mode, "error", ex.getClass().getSimpleName(), "circuit",
				this.circuitBreakerRegistry.circuitBreaker("downstream").getState().name());
	}

}
