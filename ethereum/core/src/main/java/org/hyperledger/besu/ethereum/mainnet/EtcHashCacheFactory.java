package org.hyperledger.besu.ethereum.mainnet;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.primitives.Ints;

import java.util.concurrent.ExecutionException;

public class EtcHashCacheFactory {
    public static class EtcHashDescriptor {
        private final long datasetSize;
        private final int[] cache;

        public EtcHashDescriptor(final long datasetSize, final int[] cache) {
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

    Cache<Long, EtcHashCacheFactory.EtcHashDescriptor> descriptorCache = CacheBuilder.newBuilder().maximumSize(5).build();

    public EtcHashCacheFactory.EtcHashDescriptor etcHashCacheFor(final long blockNumber) {
        final long epochIndex = EtcHash.epoch(blockNumber);
        try {
            return descriptorCache.get(epochIndex, () -> createHashCache(epochIndex, blockNumber));
        } catch (final ExecutionException ex) {
            throw new RuntimeException("Failed to create a suitable cache for EthHash calculations.", ex);
        }
    }

    private EtcHashCacheFactory.EtcHashDescriptor createHashCache(final long epochIndex, final long blockNumber) {
        final int[] cache =
                EtcHash.mkCache(Ints.checkedCast(EtcHash.cacheSize(epochIndex)), blockNumber);
        return new EtcHashCacheFactory.EtcHashDescriptor(EtcHash.datasetSize(epochIndex), cache);
    }
}
