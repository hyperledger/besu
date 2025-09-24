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

import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.primitives.Ints;

public class EthHashCacheFactory {

  public static class EthHashDescriptor {
    private final long datasetSize;
    private final int[] cache;

    public EthHashDescriptor(final long datasetSize, final int[] cache) {
      this.datasetSize = datasetSize;
      this.cache = cache;
    }

    public long getDatasetSize() {
      return datasetSize;
    }

    public int[] getCache() {
      return cache;
    }
  }

  Cache<Long, EthHashDescriptor> descriptorCache = CacheBuilder.newBuilder().maximumSize(5).build();

  public EthHashDescriptor ethHashCacheFor(
      final long blockNumber, final EpochCalculator epochCalc) {
    final long epochIndex = epochCalc.cacheEpoch(blockNumber);
    try {
      return descriptorCache.get(
          epochIndex, () -> createHashCache(epochIndex, epochCalc, blockNumber));
    } catch (final ExecutionException ex) {
      throw new RuntimeException("Failed to create a suitable cache for EthHash calculations.", ex);
    }
  }

  private EthHashDescriptor createHashCache(
      final long epochIndex, final EpochCalculator epochCalculator, final long blockNumber) {
    final int[] cache =
        EthHash.mkCache(
            Ints.checkedCast(EthHash.cacheSize(epochIndex)), blockNumber, epochCalculator);
    return new EthHashDescriptor(EthHash.datasetSize(epochIndex), cache);
  }
}
