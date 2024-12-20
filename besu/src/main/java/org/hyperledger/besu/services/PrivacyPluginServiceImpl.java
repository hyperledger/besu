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
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Privacy plugin service implementation. */
@Deprecated(since = "24.12.0")
public class PrivacyPluginServiceImpl implements PrivacyPluginService {
  private static final Logger LOG = LoggerFactory.getLogger(PrivacyPluginServiceImpl.class);

  private PrivacyPluginPayloadProvider privacyPluginPayloadProvider;
  private PrivateMarkerTransactionFactory privateMarkerTransactionFactory;

  private PrivacyGroupAuthProvider privacyGroupAuthProvider =
      (privacyGroupId, privacyUserId, blockNumber) -> true;
  private PrivacyGroupGenesisProvider privacyGroupGenesisProvider;

  /** Default Constructor. */
  public PrivacyPluginServiceImpl() {}

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

  @Override
  public PrivateMarkerTransactionFactory getPrivateMarkerTransactionFactory() {
    return privateMarkerTransactionFactory;
  }

  @Override
  public void setPrivateMarkerTransactionFactory(
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {
    this.privateMarkerTransactionFactory = privateMarkerTransactionFactory;
  }

  @Override
  public void setPrivacyGroupGenesisProvider(
      final PrivacyGroupGenesisProvider privacyGroupGenesisProvider) {
    this.privacyGroupGenesisProvider = privacyGroupGenesisProvider;
  }

  @Override
  public PrivacyGroupGenesisProvider getPrivacyGroupGenesisProvider() {
    return privacyGroupGenesisProvider;
  }
}
