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

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 可控的下游服务，用于 Resilience4j 实验。
 *
 * <p>
 * mode=ok 正常返回；mode=slow 延迟 delayMs 后返回；mode=error 返回 503。
 */
@RestController
@RequestMapping("/demo/downstream")
class DownstreamController {

	private final String instanceId = System.getenv().getOrDefault("HOSTNAME", "unknown");

	@GetMapping
	Map<String, Object> call(@RequestParam(defaultValue = "ok") String mode,
			@RequestParam(defaultValue = "2000") long delayMs) {
		switch (mode) {
			case "ok" -> {
				// 正常路径，无需额外处理
			}
			case "slow" -> sleep(delayMs);
			case "error" ->
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "simulated downstream failure");
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode: " + mode);
		}
		return Map.of("status", "ok", "mode", mode, "instance", this.instanceId, "timestamp", Instant.now().toString());
	}

	private void sleep(long delayMs) {
		try {
			Thread.sleep(delayMs);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted");
		}
	}

}
