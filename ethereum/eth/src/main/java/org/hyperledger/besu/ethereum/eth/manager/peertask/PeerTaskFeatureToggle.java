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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary class to allow easy access to the PeerTask feature toggle. This class can be removed
 * once we've properly tested the PeerTask system and want to enable it permanently
 */
public class PeerTaskFeatureToggle {
  private static final Logger LOGGER = LoggerFactory.getLogger(PeerTaskFeatureToggle.class);
  private static Boolean USE_PEER_TASK_SYSTEM = null;

  /**
   * Initialize the PeerTaskFeatureToggle with the supplied usePeerTaskSystem Boolean.
   * PeerTaskFeatureToggle may only be initialized once!
   *
   * @param usePeerTaskSystem Boolean indicating whether or not the PeerTask system should be used
   */
  public static void initialize(final Boolean usePeerTaskSystem) {
    if (USE_PEER_TASK_SYSTEM != null) {
      LOGGER.warn(
          "PeerTaskFeatureToggle has already been initialized, and cannot be initialized again");
    } else {
      USE_PEER_TASK_SYSTEM = usePeerTaskSystem;
    }
  }

  /**
   * Indicates whether or not to use the PeerTask system. PeerTaskFeatureToggle must have been
   * initialized before this method can be called!
   *
   * @return whether or not to use the PeerTask system
   * @throws IllegalStateException if the PeerTaskFeatureToggle has not been initialized
   */
  public static Boolean usePeerTaskSystem() {
    if (USE_PEER_TASK_SYSTEM == null) {
      throw new IllegalStateException(
          "PeerTaskFeatureToggle has not been initialized, but getUsePeerTaskSystem() has been called");
    }
    return USE_PEER_TASK_SYSTEM;
  }

  private PeerTaskFeatureToggle() {}
}
