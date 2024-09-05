/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import java.util.Locale;

/** The reason why a pending tx has been removed */
public interface RemovalReason {
  /**
   * From where the tx has been removed
   *
   * @return removed from item
   */
  RemovedFrom removedFrom();

  /**
   * Return a label that identify this reason to be used in the metric system.
   *
   * @return a label
   */
  String label();

  /** There are 2 kinds of removals, from a layer and from the pool. */
  enum RemovedFrom {
    /**
     * Removing from a layer, can be also seen as a <i>move</i> between layers, since it is removed
     * from the current layer and added to another layer, for example in the case the layer is full
     * and some txs need to be moved to the next layer, or in the opposite case when some txs are
     * promoted to the upper layer.
     */
    LAYER,
    /**
     * Removing from the pool, instead means that the tx is directly removed from the pool, and it
     * will not be present in any layer, for example, when it is added to an imported block, or it
     * is replaced by another tx.
     */
    POOL
  }

  /** The reason why the tx has been removed from the pool */
  enum PoolRemovalReason implements RemovalReason {
    /** Tx removed since it is confirmed on chain, as part of an imported block. */
    CONFIRMED,
    /** Tx removed since it has been replaced by another one added in the same layer. */
    REPLACED,
    /** Tx removed since it has been replaced by another one added in another layer. */
    CROSS_LAYER_REPLACED,
    /** Tx removed when the pool is full, to make space for new incoming txs. */
    DROPPED,
    /**
     * Tx removed since found invalid after it was added to the pool, for example during txs
     * selection for a new block proposal.
     */
    INVALIDATED,
    /**
     * Special case, when for a sender, discrepancies are found between the world state view and the
     * pool view, then all the txs for this sender are removed and added again. Discrepancies, are
     * rare, and can happen during a short windows when a new block is being imported and the world
     * state being updated.
     */
    RECONCILED,
    /**
     * When a pending tx is penalized its score is decreased, if at some point its score is lower
     * than the configured minimum then the pending tx is removed from the pool.
     */
    BELOW_MIN_SCORE;

    private final String label;

    PoolRemovalReason() {
      this.label = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public RemovedFrom removedFrom() {
      return RemovedFrom.POOL;
    }

    @Override
    public String label() {
      return label;
    }
  }

  /** The reason why the tx has been moved across layers */
  enum LayerMoveReason implements RemovalReason {
    /**
     * When the current layer is full, and this tx needs to be moved to the lower layer, in order to
     * free space.
     */
    EVICTED,
    /**
     * Specific to sequential layers, when a tx is removed because found invalid, then if the sender
     * has other txs with higher nonce, then a gap is created, and since sequential layers do not
     * permit gaps, txs following the invalid one need to be moved to lower layers.
     */
    FOLLOW_INVALIDATED,
    /**
     * When a tx is moved to the upper layer, since it satisfies all the requirement to be promoted.
     */
    PROMOTED,
    /**
     * When a tx is moved to the lower layer, since it, or a preceding one from the same sender,
     * does not respect anymore the requisites to stay in this layer.
     */
    DEMOTED;

    private final String label;

    LayerMoveReason() {
      this.label = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public RemovedFrom removedFrom() {
      return RemovedFrom.LAYER;
    }

    @Override
    public String label() {
      return label;
    }
  }
}
