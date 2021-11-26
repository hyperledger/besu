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
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;

public class PrivacyNodeConfiguration {

  private final boolean isFlexiblePrivacyGroupEnabled;
  private final boolean isMultitenancyEnabled;
  private final boolean isPrivacyPluginEnabled;
  private final BesuNodeConfiguration besuConfig;
  private final EnclaveKeyConfiguration keyConfig;

  PrivacyNodeConfiguration(
      final BesuNodeConfiguration besuConfig, final EnclaveKeyConfiguration keyConfig) {
    this(false, false, false, besuConfig, keyConfig);
  }

  public PrivacyNodeConfiguration(
      final boolean isFlexiblePrivacyGroupEnabled,
      final boolean isMultitenancyEnabled,
      final boolean isPrivacyPluginEnabled,
      final BesuNodeConfiguration besuConfig,
      final EnclaveKeyConfiguration keyConfig) {
    this.isFlexiblePrivacyGroupEnabled = isFlexiblePrivacyGroupEnabled;
    this.besuConfig = besuConfig;
    this.keyConfig = keyConfig;
    this.isMultitenancyEnabled = isMultitenancyEnabled;
    this.isPrivacyPluginEnabled = isPrivacyPluginEnabled;
  }

  public boolean isFlexiblePrivacyGroupEnabled() {
    return isFlexiblePrivacyGroupEnabled;
  }

  public boolean isMultitenancyEnabled() {
    return isMultitenancyEnabled;
  }

  public boolean isPrivacyPluginEnabled() {
    return isPrivacyPluginEnabled;
  }

  public BesuNodeConfiguration getBesuConfig() {
    return besuConfig;
  }

  public EnclaveKeyConfiguration getKeyConfig() {
    return keyConfig;
  }
}
