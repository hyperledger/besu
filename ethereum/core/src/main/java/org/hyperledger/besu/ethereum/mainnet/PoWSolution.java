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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class PoWSolution {
  private final long nonce;
  private final Hash mixHash;
  private final Bytes powHash;
  private final Bytes solution;

  public PoWSolution(
      final long nonce, final Hash mixHash, final Bytes solution, final Bytes powHash) {
    this.nonce = nonce;
    this.mixHash = mixHash;
    this.solution = solution;
    this.powHash = powHash;
  }

  public long getNonce() {
    return nonce;
  }

  public Hash getMixHash() {
    return mixHash;
  }

  public Bytes getPowHash() {
    return powHash;
  }

  public Bytes getSolution() {
    return solution;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PoWSolution that = (PoWSolution) o;
    return nonce == that.nonce
        && Objects.equals(mixHash, that.mixHash)
        && Objects.equals(solution, that.solution)
        && Objects.equals(powHash, that.powHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nonce, mixHash, solution, powHash);
  }
}
