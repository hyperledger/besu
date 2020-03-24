/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy;

import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfiguration;
import org.hyperledger.orion.testutil.OrionKeyConfiguration;

public class PrivacyNodeConfiguration {

  private final int privacyAddress;
  private final boolean isOnchainPrivacyGroupEnabled;
  private final BesuNodeConfiguration besuConfig;
  private final OrionKeyConfiguration orionConfig;

  PrivacyNodeConfiguration(
      final int privacyAddress,
      final BesuNodeConfiguration besuConfig,
      final OrionKeyConfiguration orionConfig) {
    this(privacyAddress, false, besuConfig, orionConfig);
  }

  PrivacyNodeConfiguration(
      final int privacyAddress,
      final boolean isOnchainPrivacyGroupEnabled,
      final BesuNodeConfiguration besuConfig,
      final OrionKeyConfiguration orionConfig) {
    this.privacyAddress = privacyAddress;
    this.isOnchainPrivacyGroupEnabled = isOnchainPrivacyGroupEnabled;
    this.besuConfig = besuConfig;
    this.orionConfig = orionConfig;
  }

  public int getPrivacyAddress() {
    return privacyAddress;
  }

  public boolean isOnchainPrivacyGroupEnabled() {
    return isOnchainPrivacyGroupEnabled;
  }

  public BesuNodeConfiguration getBesuConfig() {
    return besuConfig;
  }

  public OrionKeyConfiguration getOrionKeyConfig() {
    return orionConfig;
  }
}
