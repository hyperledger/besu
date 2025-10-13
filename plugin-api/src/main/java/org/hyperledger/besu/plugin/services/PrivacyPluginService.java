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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupAuthProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

/**
 * A service that plugins can use to define how private transactions should be handled. <br>
 * <br>
 * You must register a {@link PrivacyPluginPayloadProvider} when using this plugin and can
 * optionally register a {@link PrivateMarkerTransactionFactory} and a {@link
 * PrivacyGroupGenesisProvider}*
 */
@Deprecated(since = "24.12.0")
public interface PrivacyPluginService extends BesuService {

  /**
   * Register a provider to use when handling privacy marker transactions.
   *
   * @param privacyPluginPayloadProvider the provider to use for the privacy marker payload
   */
  void setPayloadProvider(PrivacyPluginPayloadProvider privacyPluginPayloadProvider);

  /**
   * Gets payload provider.
   *
   * @return the payload provider
   */
  PrivacyPluginPayloadProvider getPayloadProvider();

  /**
   * Register a factory to specify your own method for signing and serializing privacy marker
   * transactions.
   *
   * @param privateMarkerTransactionFactory the factory to use to build the privacy marker
   *     transaction
   */
  void setPrivateMarkerTransactionFactory(
      PrivateMarkerTransactionFactory privateMarkerTransactionFactory);

  /**
   * Gets private marker transaction factory.
   *
   * @return the private marker transaction factory
   */
  PrivateMarkerTransactionFactory getPrivateMarkerTransactionFactory();

  /**
   * Register a provider to use when auth requests for a multi-tenant environment. If you are not
   * using a multi-tenant environment you always return true.
   *
   * @param privacyGroupAuthProvider the provider to use to determine authz
   */
  void setPrivacyGroupAuthProvider(PrivacyGroupAuthProvider privacyGroupAuthProvider);

  /**
   * Gets privacy group auth provider.
   *
   * @return the privacy group auth provider
   */
  PrivacyGroupAuthProvider getPrivacyGroupAuthProvider();

  /**
   * Register a provider for initialising private state genesis
   *
   * @param privacyGroupAuthProvider the provider for the initial private state
   */
  void setPrivacyGroupGenesisProvider(PrivacyGroupGenesisProvider privacyGroupAuthProvider);

  /**
   * Gets privacy group genesis provider.
   *
   * @return the privacy group genesis provider
   */
  PrivacyGroupGenesisProvider getPrivacyGroupGenesisProvider();
}
