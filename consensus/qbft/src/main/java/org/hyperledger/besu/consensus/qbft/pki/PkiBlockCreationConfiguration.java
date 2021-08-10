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

public class PkiBlockCreationConfiguration {

  private final KeyStoreWrapper keyStore;
  private final KeyStoreWrapper trustStore;
  private final String certificateAlias;

  public PkiBlockCreationConfiguration(
      final KeyStoreWrapper keyStore,
      final KeyStoreWrapper trustStore,
      final String certificateAlias) {
    this.keyStore = keyStore;
    this.trustStore = trustStore;
    this.certificateAlias = certificateAlias;
  }

  public KeyStoreWrapper getKeyStore() {
    return keyStore;
  }

  public KeyStoreWrapper getTrustStore() {
    return trustStore;
  }

  public String getCertificateAlias() {
    return certificateAlias;
  }
}
