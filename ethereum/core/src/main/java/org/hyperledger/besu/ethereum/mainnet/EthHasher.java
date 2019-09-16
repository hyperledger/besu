/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

public interface EthHasher {

  /**
   * @param buffer At least 64 bytes long buffer to store EthHash result in
   * @param nonce Block Nonce
   * @param number Block Number
   * @param headerHash Block Header (without mix digest and nonce) Hash
   */
  void hash(byte[] buffer, long nonce, long number, byte[] headerHash);

  final class Light implements EthHasher {

    private static final EthHashCacheFactory cacheFactory = new EthHashCacheFactory();

    @Override
    public void hash(
        final byte[] buffer, final long nonce, final long number, final byte[] headerHash) {
      final EthHashCacheFactory.EthHashDescriptor cache = cacheFactory.ethHashCacheFor(number);
      final byte[] hash =
          EthHash.hashimotoLight(cache.getDatasetSize(), cache.getCache(), headerHash, nonce);
      System.arraycopy(hash, 0, buffer, 0, hash.length);
    }
  }
}
