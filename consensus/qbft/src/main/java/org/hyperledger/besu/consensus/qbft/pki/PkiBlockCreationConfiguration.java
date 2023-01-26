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

package org.hyperledger.besu.consensus.qbft.pki;

import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

/** The Pki block creation configuration. */
public class PkiBlockCreationConfiguration {

  private final KeyStoreWrapper keyStore;
  private final KeyStoreWrapper trustStore;
  private final String certificateAlias;

  /**
   * Instantiates a new Pki block creation configuration.
   *
   * @param keyStore the key store
   * @param trustStore the trust store
   * @param certificateAlias the certificate alias
   */
  public PkiBlockCreationConfiguration(
      final KeyStoreWrapper keyStore,
      final KeyStoreWrapper trustStore,
      final String certificateAlias) {
    this.keyStore = keyStore;
    this.trustStore = trustStore;
    this.certificateAlias = certificateAlias;
  }

  /**
   * Gets key store.
   *
   * @return the key store
   */
  public KeyStoreWrapper getKeyStore() {
    return keyStore;
  }

  /**
   * Gets trust store.
   *
   * @return the trust store
   */
  public KeyStoreWrapper getTrustStore() {
    return trustStore;
  }

  /**
   * Gets certificate alias.
   *
   * @return the certificate alias
   */
  public String getCertificateAlias() {
    return certificateAlias;
  }
}
