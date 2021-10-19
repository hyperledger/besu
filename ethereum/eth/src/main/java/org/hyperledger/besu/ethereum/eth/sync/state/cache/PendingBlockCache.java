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
package org.hyperledger.besu.ethereum.eth.sync.state.cache;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class PendingBlockCache extends ConcurrentHashMap<Hash, ImmutablePendingBlock> {

  private final long cacheSizePerPeer;

  public PendingBlockCache(final long cacheSizePerPeer) {
    this.cacheSizePerPeer = cacheSizePerPeer;
  }

  /**
   * Adds the specified hash in the cache if it is not already associated with a value. Otherwise
   * returns the current value.
   *
   * @return the previous value associated with the specified key, or {@code null} if there was no
   *     mapping for the hash
   */
  @Override
  public ImmutablePendingBlock putIfAbsent(
      final Hash hash, final ImmutablePendingBlock pendingBlock) {
    final ImmutablePendingBlock foundBlock = super.putIfAbsent(hash, pendingBlock);
    if (foundBlock == null) {
      removeLowestPriorityBlockWhenCacheFull(pendingBlock.nodeId());
    }
    return foundBlock;
  }

  /**
   * Removes the lowest priority block if a peer has reached the cache limit it is allowed to use
   * The highest priority blocks are those that are lowest in block height and then higher priority
   * if they were sent more recently.
   *
   * @param nodeId id of the peer
   */
  private void removeLowestPriorityBlockWhenCacheFull(final Bytes nodeId) {
    final List<ImmutablePendingBlock> blockByNodeId =
        values().stream().filter(value -> value.nodeId() == nodeId).collect(Collectors.toList());
    if (blockByNodeId.size() > cacheSizePerPeer) {
      blockByNodeId.stream()
          .min(getComparatorByBlockNumber().reversed().thenComparing(getComparatorByTimeStamp()))
          .ifPresent(value -> remove(value.block().getHash()));
    }
  }

  private Comparator<ImmutablePendingBlock> getComparatorByBlockNumber() {
    return Comparator.comparing(s -> s.block().getHeader().getNumber());
  }

  private Comparator<ImmutablePendingBlock> getComparatorByTimeStamp() {
    return Comparator.comparing(s -> s.block().getHeader().getTimestamp());
  }
}
