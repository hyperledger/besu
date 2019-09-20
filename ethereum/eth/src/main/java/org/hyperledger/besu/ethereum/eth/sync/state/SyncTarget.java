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
package org.hyperledger.besu.ethereum.eth.sync.state;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class SyncTarget {

  private final EthPeer peer;
  private final BlockHeader commonAncestor;

  public SyncTarget(final EthPeer peer, final BlockHeader commonAncestor) {
    this.peer = peer;
    this.commonAncestor = commonAncestor;
  }

  public EthPeer peer() {
    return peer;
  }

  public BlockHeader commonAncestor() {
    return commonAncestor;
  }

  public long addPeerChainEstimatedHeightListener(
      final ChainState.EstimatedHeightListener listener) {
    return peer.addChainEstimatedHeightListener(listener);
  }

  public void removePeerChainEstimatedHeightListener(final long listenerId) {
    peer.removeChainEstimatedHeightListener(listenerId);
  }

  public long estimatedTargetHeight() {
    return peer.chainState().getEstimatedHeight();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SyncTarget that = (SyncTarget) o;
    return Objects.equals(peer, that.peer) && Objects.equals(commonAncestor, that.commonAncestor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peer, commonAncestor);
  }

  @Override
  public String toString() {
    final ChainState chainState = peer.chainState();
    return MoreObjects.toStringHelper(this)
        .add(
            "height",
            (chainState.getEstimatedHeight() == 0 ? "?" : chainState.getEstimatedHeight()))
        .add("td", chainState.getEstimatedTotalDifficulty())
        .add("peer", peer)
        .toString();
  }
}
