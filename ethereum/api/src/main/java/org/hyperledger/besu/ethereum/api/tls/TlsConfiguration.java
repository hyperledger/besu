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

package org.hyperledger.besu.ethereum.api.tls;

import java.util.Objects;
import java.util.Optional;

public class TlsConfiguration {
  private final TlsStoreConfiguration keyStore;

  // trust store to verify identify of clients
  private final Optional<TlsStoreConfiguration> trustStore;

  public TlsConfiguration(
      final TlsStoreConfiguration keyStore, final Optional<TlsStoreConfiguration> trustStore) {
    this.keyStore = keyStore;
    this.trustStore = trustStore;
  }

  public TlsStoreConfiguration getKeyStore() {
    return keyStore;
  }

  public Optional<TlsStoreConfiguration> getTrustStore() {
    return trustStore;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final TlsConfiguration that = (TlsConfiguration) o;
    return keyStore.equals(that.keyStore) && trustStore.equals(that.trustStore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keyStore, trustStore);
  }
}
