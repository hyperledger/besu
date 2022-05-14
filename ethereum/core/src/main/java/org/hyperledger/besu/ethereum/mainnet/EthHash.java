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
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.BiConsumer;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;

/** Implementation of EthHash. */
public final class EthHash {

  public static final int HASH_BYTES = 64;

  public static final BigInteger TARGET_UPPER_BOUND = BigInteger.valueOf(2).pow(256);

  public static final int EPOCH_LENGTH = 30000;

  private static final int DATASET_INIT_BYTES = 1 << 30;

  private static final int DATASET_GROWTH_BYTES = 1 << 23;

  private static final int CACHE_INIT_BYTES = 1 << 24;

  private static final int CACHE_GROWTH_BYTES = 1 << 17;

  private static final int MIX_BYTES = 128;

  private static final int HASH_WORDS = 16;

  private static final int CACHE_ROUNDS = 3;

  private static final int WORD_BYTES = 4;

  private static final int DATASET_PARENTS = 256;

  private static final int ACCESSES = 64;

  private static final ThreadLocal<MessageDigest> KECCAK_512 =
      ThreadLocal.withInitial(Keccak.Digest512::new);

  /**
   * Hashimoto Light Implementation.
   *
   * @param size Dataset size for the given header hash
   * @param cache EthHash Cache
   * @param header Truncated BlockHeader hash
   * @param nonce Nonce to use for hashing
   * @return A byte array holding MixHash in its first 32 bytes and the EthHash result in the in
   *     bytes 32 to 63
   */
  public static PoWSolution hashimotoLight(
      final long size, final int[] cache, final Bytes header, final long nonce) {
    return hashimoto(header, size, nonce, (target, ind) -> calcDatasetItem(target, cache, ind));
  }

  public static PoWSolution hashimoto(
      final Bytes header,
      final long size,
      final long nonce,
      final BiConsumer<byte[], Integer> datasetLookup) {
    final int n = (int) Long.divideUnsigned(size, MIX_BYTES);
    final MessageDigest keccak512 = KECCAK_512.get();
    keccak512.update(header.toArrayUnsafe());
    keccak512.update(Longs.toByteArray(Long.reverseBytes(nonce)));
    final byte[] seed = keccak512.digest();
    final ByteBuffer mixBuffer = ByteBuffer.allocate(MIX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < MIX_BYTES / HASH_BYTES; ++i) {
      mixBuffer.put(seed);
    }
    mixBuffer.position(0);
    final int[] mix = new int[MIX_BYTES / 4];
    for (int i = 0; i < MIX_BYTES / 4; ++i) {
      mix[i] = mixBuffer.getInt();
    }
    final byte[] lookupResult = new byte[HASH_BYTES];
    final byte[] temp = new byte[MIX_BYTES];
    for (int i = 0; i < ACCESSES; ++i) {
      final int p =
          Integer.remainderUnsigned(
              fnv(i ^ readLittleEndianInt(seed, 0), mix[i % (MIX_BYTES / WORD_BYTES)]), n);
      for (int j = 0; j < MIX_BYTES / HASH_BYTES; ++j) {
        datasetLookup.accept(lookupResult, 2 * p + j);
        System.arraycopy(lookupResult, 0, temp, j * HASH_BYTES, HASH_BYTES);
      }
      fnvHash(mix, temp);
    }
    final int[] cmix = new int[mix.length / 4];
    for (int i = 0; i < mix.length; i += 4) {
      cmix[i / 4] = fnv(fnv(fnv(mix[i], mix[i + 1]), mix[i + 2]), mix[i + 3]);
    }
    final byte[] result = new byte[32 + 32];
    intToByte(result, cmix);
    final MessageDigest keccak256 = DirectAcyclicGraphSeed.KECCAK_256.get();
    keccak256.update(seed);
    keccak256.update(result, 0, 32);
    try {
      keccak256.digest(result, 32, 32);
    } catch (final DigestException ex) {
      throw new IllegalStateException(ex);
    }

    return new PoWSolution(
        nonce,
        Hash.wrap(Bytes32.wrap(Arrays.copyOf(result, 32))),
        Bytes32.wrap(result, 32),
        header);
  }

  /**
   * Calculates a dataset item and writes it to a given buffer.
   *
   * @param buffer Buffer to store dataset item in
   * @param cache EthHash Cache
   * @param index Index of the dataset item to calculate
   */
  public static void calcDatasetItem(final byte[] buffer, final int[] cache, final int index) {
    final int rows = cache.length / HASH_WORDS;
    final int[] mixInts = new int[HASH_BYTES / 4];
    final int offset = index % rows * HASH_WORDS;
    mixInts[0] = cache[offset] ^ index;
    System.arraycopy(cache, offset + 1, mixInts, 1, HASH_WORDS - 1);
    intToByte(buffer, mixInts);
    final MessageDigest keccak512 = KECCAK_512.get();
    keccak512.update(buffer);
    try {
      keccak512.digest(buffer, 0, HASH_BYTES);
      ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(mixInts);
      for (int i = 0; i < DATASET_PARENTS; ++i) {
        fnvHash(
            mixInts,
            cache,
            Integer.remainderUnsigned(fnv(index ^ i, mixInts[i % 16]), rows) * HASH_WORDS);
      }
      intToByte(buffer, mixInts);
      keccak512.update(buffer);
      keccak512.digest(buffer, 0, HASH_BYTES);
    } catch (final DigestException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Hashes a BlockHeader without its nonce and MixHash.
   *
   * @param header Block Header
   * @return Truncated BlockHeader hash
   */
  public static Bytes32 hashHeader(final SealableBlockHeader header) {
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
    if (header.getBaseFee().isPresent()) {
      out.writeUInt256Scalar(header.getBaseFee().get());
    }
    out.endList();
    return Bytes32.wrap(
        DirectAcyclicGraphSeed.KECCAK_256.get().digest(out.encoded().toArrayUnsafe()));
  }

  /**
   * Generates the EthHash cache for given parameters.
   *
   * @param cacheSize Size of the cache to generate
   * @param block Block Number to generate cache for
   * @param epochCalculator EpochCalculator used to determine current epoch length
   * @return EthHash Cache
   */
  public static int[] mkCache(
      final int cacheSize, final long block, final EpochCalculator epochCalculator) {
    final MessageDigest keccak512 = KECCAK_512.get();
    keccak512.update(DirectAcyclicGraphSeed.dagSeed(block, epochCalculator));
    final int rows = cacheSize / HASH_BYTES;
    final byte[] cache = new byte[rows * HASH_BYTES];
    try {
      keccak512.digest(cache, 0, HASH_BYTES);
    } catch (final DigestException ex) {
      throw new IllegalStateException(ex);
    }
    for (int i = 1; i < rows; ++i) {
      keccak512.update(cache, (i - 1) * HASH_BYTES, HASH_BYTES);
      try {
        keccak512.digest(cache, i * HASH_BYTES, HASH_BYTES);
      } catch (final DigestException ex) {
        throw new IllegalStateException(ex);
      }
    }
    final byte[] temp = new byte[HASH_BYTES];
    for (int i = 0; i < CACHE_ROUNDS; ++i) {
      for (int j = 0; j < rows; ++j) {
        final int offset = j * HASH_BYTES;
        for (int k = 0; k < HASH_BYTES; ++k) {
          temp[k] =
              (byte)
                  (cache[(j - 1 + rows) % rows * HASH_BYTES + k]
                      ^ cache[
                          Integer.remainderUnsigned(readLittleEndianInt(cache, offset), rows)
                                  * HASH_BYTES
                              + k]);
        }
        keccak512.update(temp);
        try {
          keccak512.digest(temp, 0, HASH_BYTES);
        } catch (final DigestException ex) {
          throw new IllegalStateException(ex);
        }
        System.arraycopy(temp, 0, cache, offset, HASH_BYTES);
      }
    }
    final int[] result = new int[cache.length / 4];
    ByteBuffer.wrap(cache).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(result);
    return result;
  }

  /**
   * Calculates EthHash Cache size at a given epoch.
   *
   * @param epoch EthHash Epoch
   * @return Cache size
   */
  public static long cacheSize(final long epoch) {
    long size = epoch * CACHE_GROWTH_BYTES + CACHE_INIT_BYTES - HASH_BYTES;
    while (!isPrime(Long.divideUnsigned(size, HASH_BYTES))) {
      size -= 2 * HASH_BYTES;
    }
    return size;
  }

  /**
   * Calculates EthHash DataSet size at a given epoch.
   *
   * @param epoch EthHash Epoch
   * @return DataSet size
   */
  public static long datasetSize(final long epoch) {
    long size = epoch * DATASET_GROWTH_BYTES + DATASET_INIT_BYTES - MIX_BYTES;
    while (!isPrime(Long.divideUnsigned(size, MIX_BYTES))) {
      size -= 2 * MIX_BYTES;
    }
    return size;
  }

  private static boolean isPrime(final long num) {
    if (num > 2 && (num & 1) == 0) {
      return false;
    }
    for (long i = 3; i * i <= num; i += 2) {
      if (num % i == 0) {
        return false;
      }
    }
    return true;
  }

  private static int readLittleEndianInt(final byte[] buffer, final int offset) {
    return Ints.fromBytes(
        buffer[offset + 3], buffer[offset + 2], buffer[offset + 1], buffer[offset]);
  }

  private static void intToByte(final byte[] target, final int[] ints) {
    final ByteBuffer buffer = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN);
    for (final int i : ints) {
      buffer.putInt(i);
    }
  }

  private static void fnvHash(final int[] mix, final byte[] cache) {
    for (int i = 0; i < mix.length; i++) {
      mix[i] = fnv(mix[i], readLittleEndianInt(cache, i * Integer.BYTES));
    }
  }

  private static void fnvHash(final int[] mix, final int[] cache, final int offset) {
    for (int i = 0; i < mix.length; i++) {
      mix[i] = fnv(mix[i], cache[offset + i]);
    }
  }

  private static int fnv(final int a, final int b) {
    return a * 0x01000193 ^ b;
  }
}
