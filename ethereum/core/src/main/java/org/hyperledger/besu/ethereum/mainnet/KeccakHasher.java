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

import java.security.MessageDigest;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;

/**
 * Hasher for Keccak-256 PoW.
 *
 * <p>Block is valid if keccak256(keccak256(rlp(unsealed header)), nonce) is less than or equal to
 * 2^256 / difficulty mixhash = keccak256(rlp(unsealed header)) which is stored in the block.
 * Currently this process is ethash(rlp(unsealed header)) So to validate a block we check
 * keccak256(mixhash, nonce) is less than or equal to 2^256 / difficulty
 */
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
      final Bytes prePowHash) {

    MessageDigest digest = KECCAK_256.get();
    digest.update(prePowHash.toArrayUnsafe());
    digest.update(Bytes.ofUnsignedLong(nonce).toArrayUnsafe());
    Bytes32 solution = Bytes32.wrap(digest.digest());
    Hash mixHash = Hash.wrap(solution);

    return new PoWSolution(nonce, mixHash, solution, prePowHash);
  }
}
