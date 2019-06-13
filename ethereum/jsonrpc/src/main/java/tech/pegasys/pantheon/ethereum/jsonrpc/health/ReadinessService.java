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
package tech.pegasys.pantheon.ethereum.jsonrpc.health;

import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReadinessService extends HealthService {
  private static final Logger LOG = LogManager.getLogger();

  public ReadinessService(final P2PNetwork p2PNetwork) {
    super(buildHealthCheck(p2PNetwork));
  }

  private static HealthCheck buildHealthCheck(final P2PNetwork p2PNetwork) {
    return () -> {
      LOG.debug("Invoking readiness service.");
      if (p2PNetwork.isP2pEnabled()) {
        return p2PNetwork.isListening();
      }
      return true;
    };
  }
}
