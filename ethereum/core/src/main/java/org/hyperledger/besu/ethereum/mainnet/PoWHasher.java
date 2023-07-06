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

import org.apache.tuweni.bytes.Bytes;

public interface PoWHasher {

  PoWHasher ETHASH_LIGHT = new EthashLight();
  PoWHasher UNSUPPORTED = new Unsupported();

  /**
   * Hash of a particular block and nonce.
   *
   * @param nonce Block Nonce
   * @param number Block Number
   * @param epochCalc EpochCalculator for calculating epoch
   * @param prePowHash Block Header (without mix digest and nonce) Hash
   * @return the PoW solution computed by the hashing function
   */
  PoWSolution hash(long nonce, long number, EpochCalculator epochCalc, Bytes prePowHash);

  /** Implementation of Ethash Hashimoto Light Implementation. */
  final class EthashLight implements PoWHasher {

    private static final EthHashCacheFactory cacheFactory = new EthHashCacheFactory();

    private EthashLight() {}

    @Override
    public PoWSolution hash(
        final long nonce,
        final long number,
        final EpochCalculator epochCalc,
        final Bytes prePowHash) {
      final EthHashCacheFactory.EthHashDescriptor cache =
          cacheFactory.ethHashCacheFor(number, epochCalc);
      final PoWSolution solution =
          EthHash.hashimotoLight(cache.getDatasetSize(), cache.getCache(), prePowHash, nonce);
      return solution;
    }
  }

  /** Implementation of an inoperative hasher. */
  final class Unsupported implements PoWHasher {

    private Unsupported() {}

    @Override
    public PoWSolution hash(
        final long nonce,
        final long number,
        final EpochCalculator epochCalc,
        final Bytes prePowHash) {
      throw new UnsupportedOperationException("Hashing is unsupported");
    }
  }
}
