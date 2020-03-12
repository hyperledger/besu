/*
 * Copyright 2020 Whiteblock Inc.
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

import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.security.MessageDigest;

import com.google.common.primitives.Longs;
import org.bouncycastle.jcajce.provider.digest.Keccak;

/** Implementation of Keccak256Pow. */
public final class Keccak256Pow {

  public static final BigInteger TARGET_UPPER_BOUND = BigInteger.valueOf(2).pow(256);

  private static final ThreadLocal<MessageDigest> KECCAK_256 =
      ThreadLocal.withInitial(Keccak.Digest256::new);

  /**
   * Keccak256 Proof of Work Implementation.
   *
   * @param header Truncated BlockHeader hash
   * @param nonce Nonce to use for hashing
   * @return TODO: A byte array holding MixHash in its first 32 bytes and the EthHash result in the
   *     in bytes 32 to 63
   */
  public static byte[] keccak256Pow(final byte[] header, final long nonce) {
    final MessageDigest keccak256 = KECCAK_256.get();
    keccak256.update(header);
    keccak256.update(Longs.toByteArray(Long.reverseBytes(nonce)));
    return keccak256.digest();
  }

  /**
   * Hashes a BlockHeader without its nonce and MixHash.
   *
   * @param header Block Header
   * @return Truncated BlockHeader hash
   */
  public static byte[] hashHeader(final SealableBlockHeader header) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(header.getParentHash());
    out.writeBytes(header.getOmmersHash());
    out.writeBytes(header.getCoinbase());
    out.writeBytes(header.getStateRoot());
    out.writeBytes(header.getTransactionsRoot());
    out.writeBytes(header.getReceiptsRoot());
    out.writeBytes(header.getLogsBloom());
    out.writeUInt256Scalar(header.getDifficulty());
    out.writeLongScalar(header.getNumber());
    out.writeLongScalar(header.getGasLimit());
    out.writeLongScalar(header.getGasUsed());
    out.writeLongScalar(header.getTimestamp());
    out.writeBytes(header.getExtraData());
    out.endList();
    return DirectAcyclicGraphSeed.KECCAK_256.get().digest(out.encoded().toArray());
  }
}
