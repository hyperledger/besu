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

import org.hyperledger.besu.ethereum.core.Hash;

import java.util.concurrent.ConcurrentHashMap;

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
   * @throws IndexOutOfBoundsException if the limit of the number of blocks has been reached for
   *     this nodeId
   */
  @Override
  public ImmutablePendingBlock putIfAbsent(
      final Hash hash, final ImmutablePendingBlock pendingBlock) throws IndexOutOfBoundsException {
    if (getPeerWeight(pendingBlock.nodeId()) >= cacheSizePerPeer) {
      throw new IndexOutOfBoundsException();
    }
    return super.putIfAbsent(hash, pendingBlock);
  }

  /**
   * Returns the number of pending blocks from a node that are stored in the cache
   *
   * @param nodeId the peer ID
   * @return the number of elements in the cache coming from this node
   */
  private long getPeerWeight(final Bytes nodeId) {
    return values().stream().filter(value -> value.nodeId() == nodeId).count();
  }
}
