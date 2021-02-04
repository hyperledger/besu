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

public interface PoWHasher {

  PoWHasher LIGHT = new Light();
  PoWHasher NOOP = new NoOp();

  /**
   * Hash of a particular block and nonce.
   *
   * @param buffer At least 64 bytes long buffer to store EthHash result in
   * @param nonce Block Nonce
   * @param number Block Number
   * @param epochCalc EpochCalculator for calculating epoch
   * @param headerHash Block Header (without mix digest and nonce) Hash
   */
  void hash(byte[] buffer, long nonce, long number, EpochCalculator epochCalc, byte[] headerHash);

  /** Implementation of Ethash Hashimoto Light Implementation. */
  final class Light implements PoWHasher {

    private static final EthHashCacheFactory cacheFactory = new EthHashCacheFactory();

    private Light() {}

    @Override
    public void hash(
        final byte[] buffer,
        final long nonce,
        final long number,
        final EpochCalculator epochCalc,
        final byte[] headerHash) {
      final EthHashCacheFactory.EthHashDescriptor cache =
          cacheFactory.ethHashCacheFor(number, epochCalc);
      final byte[] hash =
          EthHash.hashimotoLight(cache.getDatasetSize(), cache.getCache(), headerHash, nonce);
      System.arraycopy(hash, 0, buffer, 0, hash.length);
    }
  }

  /** Implementation of an inoperative hasher. */
  final class NoOp implements PoWHasher {

    private NoOp() {}

    @Override
    public void hash(
        final byte[] buffer,
        final long nonce,
        final long number,
        final EpochCalculator epochCalc,
        final byte[] headerHash) {
      throw new UnsupportedOperationException("Hashing is unsupported");
    }
  }
}
