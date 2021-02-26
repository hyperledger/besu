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
package org.hyperledger.besu.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import org.bouncycastle.crypto.params.ECDomainParameters;

public class KeyPair {

  private final SECPPrivateKey privateKey;
  private final SECPPublicKey publicKey;

  public KeyPair(final SECPPrivateKey privateKey, final SECPPublicKey publicKey) {
    checkNotNull(privateKey);
    checkNotNull(publicKey);
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  public static KeyPair create(
      final SECPPrivateKey privateKey, final ECDomainParameters curve, final String algorithm) {
    return new KeyPair(privateKey, SECPPublicKey.create(privateKey, curve, algorithm));
  }

  @Override
  public int hashCode() {
    return Objects.hash(privateKey, publicKey);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof KeyPair)) {
      return false;
    }

    final KeyPair that = (KeyPair) other;
    return this.privateKey.equals(that.privateKey) && this.publicKey.equals(that.publicKey);
  }

  public SECPPrivateKey getPrivateKey() {
    return privateKey;
  }

  public SECPPublicKey getPublicKey() {
    return publicKey;
  }
}
