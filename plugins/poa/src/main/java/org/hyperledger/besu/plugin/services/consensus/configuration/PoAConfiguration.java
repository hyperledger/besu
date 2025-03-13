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

/** The PoA configuration. */
public class PoAConfiguration {

  private final boolean isQbftEarlyRoundChange;

  /**
   * Instantiates a new PoA configuration.
   *
   * @param isQbftEarlyRoundChange is early QBFT round change enabled
   */
  public PoAConfiguration(final boolean isQbftEarlyRoundChange) {
    this.isQbftEarlyRoundChange = isQbftEarlyRoundChange;
  }

  /**
   * Is hearly QBFT round change enabled
   *
   * @return the boolean
   */
  public boolean isQbftEarlyRoundChange() {
    return isQbftEarlyRoundChange;
  }
}
