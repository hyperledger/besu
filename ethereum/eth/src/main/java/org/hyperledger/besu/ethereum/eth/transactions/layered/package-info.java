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

/**
 * This implements a new transaction pool (txpool for brevity), with the main goal to better manage
 * nonce gaps, i.e. the possibility that the list of transactions that we see for a sender could not
 * be in order neither contiguous, that could happen just of the way there are broadcast on the p2p
 * network or intentionally to try to spam the txpool with non-executable transactions (transactions
 * that could not be included in a future block), so the goal is to try to keep in the pool
 * transactions that could be selected for a future block proposal, and at the same time, without
 * penalizing legitimate unordered transactions, that are only temporary non-executable.
 *
 * <p>It is enabled by default on public networks, to switch to another implementation use the
 * option {@code tx-pool}
 *
 * <p>The main idea is to organize the txpool in an arbitrary number of layers, where each layer has
 * specific rules and constraints that determine if a transaction belong or not to that layer and
 * also the way transactions move across layers.
 *
 * <p>Some design choices that apply to all layers are that a transaction can only be in one layer
 * at any time, and that layers are chained by priority, so the first layer has the transactions
 * that are candidate for a block proposal, and the last layer basically is where transactions are
 * dropped. Layers are meant to be added and removed in case of specific future needs. When adding a
 * new transaction, it is first tried on the first layer, if it is not accepted then the next one is
 * tried and so on. Layers could be limited by transaction number of by space, and when a layer if
 * full, it overflows to the next one and so on, instead when some space is freed, usually when
 * transactions are removed since confirmed in a block, transactions from the next layer are
 * promoted until there is space.
 *
 * <p>Some layers could make use of the score of a pending transactions, to push back in the rank
 * those pending transactions that have been penalized.
 *
 * <p>Layers are not thread safe, since they are not meant to be accessed directly, and all the
 * synchronization is managed at the level of {@link
 * org.hyperledger.besu.ethereum.eth.transactions.layered.LayeredPendingTransactions
 * LayeredPendingTransactions} class.
 *
 * <p>The current implementation is based on 3 layers, plus the last one that just drop every
 * transaction when the previous layers are full. The 3 layers are, in order:
 *
 * <ul>
 *   <li>Prioritized
 *   <li>Ready
 *   <li>Sparse
 * </ul>
 *
 * <p>Prioritized: This is where candidate transactions are selected for creating a new block.
 * Transactions ordered by score and then effective priority fee, and it is limited by size, 2000 by
 * default, to reduce the overhead of the sorting and because that number is enough to fill any
 * block, at the current gas limit. Does not allow nonce gaps, and the first transaction for each
 * sender must be the next one for that sender. Eviction is done removing the transaction with the
 * higher nonce for the sender of the less score and less effective priority fee transaction, to
 * avoid creating nonce gaps, evicted transactions go into the next layer Ready.
 *
 * <p>Ready: Similar to the Prioritized, it does not allow nonce gaps, and the first transaction for
 * each sender must be the next one for that sender, but it is limited by space instead of count,
 * thus allowing many more transactions, think about this layer like a buffer for the Prioritized.
 * Since it is meant to keep ten to hundreds of thousand of transactions, it does not have a full
 * ordering, like the previous, but only the first transaction for each sender is ordered using a
 * stable value that is score and then max fee per gas. Eviction is the same as the Prioritized, and
 * evicted transaction go into the next layer Sparse.
 *
 * <p>Sparse: This is the first layer where nonce gaps are allowed and where the first transaction
 * for a sender could not be the next expected one for that sender. The main purpose of this layer
 * is to act as a purgatory for temporary unordered and/or non-contiguous transactions, so that they
 * could become ready asap the missing transactions arrive, or they are eventually evicted. It also
 * keeps the less valuable ready transactions, that are evicted from the previous layer. It is
 * limited by space, and eviction select the oldest transaction first, that is sent to the End Layer
 * that just drop it. When promoting to the prev layer Ready, only transactions that will not create
 * nonce gaps are selected, for that we need to keep track of the nonce distance for each sender. So
 * we can say that is ordered by nonce distance for promotion.
 */
package org.hyperledger.besu.ethereum.eth.transactions.layered;
