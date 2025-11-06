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
package org.hyperledger.besu.ethereum.mainnet.staterootcommitter;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StateRootCommitterImplBal implements StateRootCommitter {
  private static final Logger LOG = LoggerFactory.getLogger(StateRootCommitterImplBal.class);

  private final CompletableFuture<Hash> balRootFuture;
  private final BalConfiguration balConfiguration;
  private final Duration balRootTimeout;
  private final StateRootCommitterImplSync computeAndCommitRoot = new StateRootCommitterImplSync();

  StateRootCommitterImplBal(
      final CompletableFuture<Hash> balRootFuture,
      final BalConfiguration balConfiguration,
      final Duration balRootTimeout) {
    this.balRootFuture = balRootFuture;
    this.balConfiguration = balConfiguration;
    this.balRootTimeout = balRootTimeout;
  }

  @Override
  public Hash computeRootAndCommit(
      final MutableWorldState worldState,
      final WorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg) {

    final Hash balRoot = waitForBalRoot();

    final Hash computed =
        computeAndCommitRoot.computeRootAndCommit(worldState, stateUpdater, blockHeader, cfg);

    if (!computed.equals(balRoot)) {
      final String mismatchMessage =
          String.format("BAL root mismatch: computed %s vs BAL %s", computed, balRoot);
      if (balConfiguration.isBalLenientOnMismatch()) {
        LOG.error(mismatchMessage);
        return computed;
      }
      throw new IllegalStateException(mismatchMessage);
    }

    return computed;
  }

  @Override
  public void cancel() {
    balRootFuture.cancel(true);
  }

  private Hash waitForBalRoot() {
    try {
      if (balRootTimeout.isNegative()) {
        return balRootFuture.join();
      }
      return balRootFuture.get(balRootTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (final TimeoutException e) {
      balRootFuture.cancel(true);
      throw new IllegalStateException(
          String.format("Timed out waiting %s for BAL state root", balRootTimeout), e);
    } catch (final ExecutionException e) {
      throw new IllegalStateException("Failed to compute BAL state root", e.getCause());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for BAL state root", e);
    }
  }
}
