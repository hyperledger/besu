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
package org.hyperledger.besu.plugin.services.consensus.configuration;

import static org.hyperledger.besu.plugin.services.consensus.configuration.PoACLIOptions.DEFAULT_IS_QBFT_EARLY_ROUND_CHANGE;

/** The PoA configuration builder. */
public class PoAConfigurationBuilder {

  private boolean isQbftEarlyRoundChange = DEFAULT_IS_QBFT_EARLY_ROUND_CHANGE;

  /** Instantiates a new Rocks db configuration builder. */
  public PoAConfigurationBuilder() {}

  /**
   * Is QBFT early round change enabled
   *
   * @param isQbftEarlyRoundChange is QBFT early round change enabled
   * @return the PoAconfiguration builder
   */
  public PoAConfigurationBuilder isQbftEarlyRoundChange(final boolean isQbftEarlyRoundChange) {
    this.isQbftEarlyRoundChange = isQbftEarlyRoundChange;
    return this;
  }

  /**
   * From.
   *
   * @param configuration the configuration
   * @return the PoA configuration builder
   */
  public static PoAConfigurationBuilder from(final PoAConfiguration configuration) {
    return new PoAConfigurationBuilder()
        .isQbftEarlyRoundChange(configuration.isQbftEarlyRoundChange());
  }

  /**
   * Build rocks db configuration.
   *
   * @return the rocks db configuration
   */
  public PoAConfiguration build() {
    return new PoAConfiguration(isQbftEarlyRoundChange);
  }
}
