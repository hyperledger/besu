/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.health;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.health.HealthService.HealthCheck;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.health.HealthService.ParamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LivenessCheck implements HealthCheck {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public boolean isHealthy(final ParamSource params) {
    LOG.debug("Invoking liveness check.");
    return true;
  }
}
