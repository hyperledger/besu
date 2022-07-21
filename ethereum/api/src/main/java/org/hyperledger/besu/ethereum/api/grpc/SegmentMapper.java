package org.hyperledger.besu.ethereum.api.grpc;

import org.hyperledger.besu.ethereum.api.grpc.GetRequest.Segment;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class SegmentMapper {
  final BiMap<KeyValueSegmentIdentifier, Segment> segmentBiMap = HashBiMap.create();

  public SegmentMapper() {
    segmentBiMap.put(KeyValueSegmentIdentifier.BLOCKCHAIN, Segment.BLOCKCHAIN);
    segmentBiMap.put(KeyValueSegmentIdentifier.WORLD_STATE, Segment.WORLD_STATE);
    segmentBiMap.put(KeyValueSegmentIdentifier.PRIVATE_TRANSACTIONS, Segment.PRIVATE_TRANSACTIONS);
    segmentBiMap.put(KeyValueSegmentIdentifier.PRIVATE_STATE, Segment.PRIVATE_STATE);
    segmentBiMap.put(KeyValueSegmentIdentifier.PRUNING_STATE, Segment.PRUNING_STATE);
    segmentBiMap.put(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE, Segment.ACCOUNT_INFO_STATE);
    segmentBiMap.put(KeyValueSegmentIdentifier.CODE_STORAGE, Segment.CODE_STORAGE);
    segmentBiMap.put(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE, Segment.ACCOUNT_STORAGE_STORAGE);
    segmentBiMap.put(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE, Segment.TRIE_BRANCH_STORAGE);
    segmentBiMap.put(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE, Segment.TRIE_LOG_STORAGE);
    segmentBiMap.put(
        KeyValueSegmentIdentifier.GOQUORUM_PRIVATE_WORLD_STATE,
        Segment.GOQUORUM_PRIVATE_WORLD_STATE);
    segmentBiMap.put(
        KeyValueSegmentIdentifier.GOQUORUM_PRIVATE_STORAGE, Segment.GOQUORUM_PRIVATE_STORAGE);
    segmentBiMap.put(
        KeyValueSegmentIdentifier.BACKWARD_SYNC_HEADERS, Segment.BACKWARD_SYNC_HEADERS);
    segmentBiMap.put(KeyValueSegmentIdentifier.BACKWARD_SYNC_BLOCKS, Segment.BACKWARD_SYNC_BLOCKS);
    segmentBiMap.put(KeyValueSegmentIdentifier.BACKWARD_SYNC_CHAIN, Segment.BACKWARD_SYNC_CHAIN);
  }

  public KeyValueSegmentIdentifier getKeyValueSegment(final Segment segment) {
    return segmentBiMap.inverse().get(segment);
  }

  public Segment getGrpcSegment(final SegmentIdentifier segmentIdentifier) {
    return segmentBiMap.get(segmentIdentifier);
  }
}
