/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.core.keys;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum BlsThresholdCryptoSystem {
  ALT_BN_128_WITH_KECCAK256(Constants.ALT_BN_128_WITH_KECCAK256);

  private static Logger LOG = LogManager.getLogger();

  private static class Constants {
    private static final int ALT_BN_128_WITH_KECCAK256 = 1;
  }

  public int value;

  BlsThresholdCryptoSystem(final int val) {
    this.value = val;
  }

  public static BlsThresholdCryptoSystem create(final int val) {
    switch (val) {
      case Constants.ALT_BN_128_WITH_KECCAK256:
        return ALT_BN_128_WITH_KECCAK256;
      default:
        String error = "Unknown BlsThresholdCryptoSystem: " + val;
        LOG.error(error);
        throw new RuntimeException(error);
    }
  }
}
