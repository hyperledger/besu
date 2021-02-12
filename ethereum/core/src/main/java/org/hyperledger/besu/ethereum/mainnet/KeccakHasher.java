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

import java.security.MessageDigest;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;

/** Hasher for Keccak-256 PoW. */
public class KeccakHasher implements PoWHasher {

  public static final KeccakHasher KECCAK256 = new KeccakHasher();

  private KeccakHasher() {}

  private static final ThreadLocal<MessageDigest> KECCAK_256 =
      ThreadLocal.withInitial(Keccak.Digest256::new);

  @Override
  public PoWSolution hash(
      final long nonce,
      final long number,
      final EpochCalculator epochCalc,
      final byte[] prePowHash) {
    Bytes input = Bytes.wrap(Bytes.wrap(prePowHash), Bytes.ofUnsignedLong(nonce));

    MessageDigest digest = KECCAK_256.get();
    digest.update(input.toArrayUnsafe());
    byte[] result = digest.digest();
    Bytes32 resultBytes = Bytes32.wrap(result);

    PoWSolution solution = new PoWSolution(nonce, Hash.wrap(resultBytes), resultBytes, prePowHash);
    return solution;
  }
}
