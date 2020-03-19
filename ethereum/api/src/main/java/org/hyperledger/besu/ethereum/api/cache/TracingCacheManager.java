package org.hyperledger.besu.ethereum.api.cache;

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.heap;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.plugin.data.PropagatedBlockContext;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ehcache.Cache;
import org.ehcache.CacheManager;

public class TracingCacheManager {
  private static final Logger LOG = LogManager.getLogger();
  private static final int CACHE_SIZE = 1000;
  private static final String CACHE_NAME = "block-trace-cache";
  private final BlockTracer tracer;
  private final DebugOperationTracer debugOperationTracer;
  private Cache<Long, BlockTrace> blockTraceCache;
  private static Optional<TracingCacheManager> instance = Optional.empty();

  public TracingCacheManager(final BlockTracer tracer) {
    this.tracer = tracer;
    debugOperationTracer = new DebugOperationTracer(TraceOptions.DEFAULT);
    try (CacheManager cacheManager =
        newCacheManagerBuilder()
            .withCache(
                CACHE_NAME,
                newCacheConfigurationBuilder(Long.class, BlockTrace.class, heap(CACHE_SIZE)))
            .build(true)) {
      blockTraceCache = cacheManager.getCache(CACHE_NAME, Long.class, BlockTrace.class);
    }
  }

  public void onNewBlock(final PropagatedBlockContext blockContext) {
    LOG.info("New block detected, starting to cache tracing data.");
    tracer
        .trace(Hash.fromPlugin(blockContext.getBlockHeader().getBlockHash()), debugOperationTracer)
        .ifPresent(
            blockTrace ->
                blockTraceCache.put(blockContext.getBlockHeader().getNumber(), blockTrace));
  }

  public Optional<BlockTrace> blockTraceAt(final long blockNumber) {
    LOG.info("Looking for cache at block number: {}", blockNumber);
    LOG.info(
        "Cached data present for block {}: {}",
        blockNumber,
        blockTraceCache.containsKey(blockNumber));
    return Optional.ofNullable(blockTraceCache.get(blockNumber));
  }

  public static Optional<TracingCacheManager> getInstance() {
    return instance;
  }

  public static void setInstance(final Optional<TracingCacheManager> instance) {
    TracingCacheManager.instance = instance;
  }
}
