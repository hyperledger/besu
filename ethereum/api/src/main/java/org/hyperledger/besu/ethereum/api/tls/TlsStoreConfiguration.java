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

/** Points to PKCS#12 format keystore which contains key/certificate */
public class TlsStoreConfiguration {
  private final String storePath;
  private final String storePassword;

  public TlsStoreConfiguration(final String storePath, final String storePassword) {
    this.storePath = storePath;
    this.storePassword = storePassword;
  }

  public String getStorePath() {
    return storePath;
  }

  public String getStorePassword() {
    return storePassword;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final TlsStoreConfiguration that = (TlsStoreConfiguration) o;
    return storePath.equals(that.storePath) && storePassword.equals(that.storePassword);
  }

  @Override
  public int hashCode() {
    return Objects.hash(storePath, storePassword);
  }
}
