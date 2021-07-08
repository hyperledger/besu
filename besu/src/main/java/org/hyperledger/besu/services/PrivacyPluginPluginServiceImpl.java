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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupAuthProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyPluginPluginServiceImpl implements PrivacyPluginService {
  private static final Logger LOG = LogManager.getLogger();

  private PrivacyPluginPayloadProvider privacyPluginPayloadProvider;

  private PrivacyGroupAuthProvider privacyGroupAuthProvider =
      (privacyGroupId, privacyUserId, blockNumber) -> true;

  @Override
  public void setPayloadProvider(final PrivacyPluginPayloadProvider privacyPluginPayloadProvider) {
    this.privacyPluginPayloadProvider = privacyPluginPayloadProvider;
  }

  @Override
  public PrivacyPluginPayloadProvider getPayloadProvider() {
    if (privacyPluginPayloadProvider == null) {
      LOG.error(
          "You must register a PrivacyPluginService and register a PrivacyPluginPayloadProvider by calling setPayloadProvider when enabling privacy plugin!");
    }
    return privacyPluginPayloadProvider;
  }

  @Override
  public void setPrivacyGroupAuthProvider(final PrivacyGroupAuthProvider privacyGroupAuthProvider) {
    this.privacyGroupAuthProvider = privacyGroupAuthProvider;
  }

  @Override
  public PrivacyGroupAuthProvider getPrivacyGroupAuthProvider() {
    return privacyGroupAuthProvider;
  }
}
