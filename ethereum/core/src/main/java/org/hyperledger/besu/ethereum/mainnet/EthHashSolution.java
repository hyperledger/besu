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

import org.hyperledger.besu.ethereum.core.Hash;

public class EthHashSolution {
  private final long nonce;
  private final Hash mixHash;
  private final byte[] powHash;

  public EthHashSolution(final long nonce, final Hash mixHash, final byte[] powHash) {
    this.nonce = nonce;
    this.mixHash = mixHash;
    this.powHash = powHash;
  }

  public long getNonce() {
    return nonce;
  }

  public Hash getMixHash() {
    return mixHash;
  }

  public byte[] getPowHash() {
    return powHash;
  }
}
