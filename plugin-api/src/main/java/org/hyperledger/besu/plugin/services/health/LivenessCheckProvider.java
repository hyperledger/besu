/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugin.services.health;

/**
 * Provider for liveness health checks.
 *
 * <p>A simple implementation can look like:
 *
 * <pre>{@code
 * context
 *    .getService(HealthCheckService.class)
 *    .get()
 *    .registerLivenessCheckProvider((paramSource) -> {
 *        // Your logic here
 *        return true;
 *    });
 * }</pre>
 */
@FunctionalInterface
public interface LivenessCheckProvider {
  /**
   * Perform the liveness check.
   *
   * @param paramSource source for health check parameters
   * @return true if the check passes, false otherwise
   */
  boolean isHealthy(ParamSource paramSource);
}
