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
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class StateRootCommitterImplBal implements StateRootCommitter {
  private final CompletableFuture<Hash> balRootFuture;
  private final boolean trustBalRoot;
  private final Duration balRootTimeout;
  private final StateRootCommitterImplSync computeAndCommitRoot = new StateRootCommitterImplSync();

  StateRootCommitterImplBal(
      final CompletableFuture<Hash> balRootFuture,
      final boolean trustBalRoot,
      final Duration balRootTimeout) {
    this.balRootFuture = balRootFuture;
    this.trustBalRoot = trustBalRoot;
    this.balRootTimeout = balRootTimeout;
  }

  @Override
  public Hash computeRootAndCommit(
      final PathBasedWorldState worldState,
      final PathBasedWorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg) {

    final Hash balRoot = waitForBalRoot();

    if (trustBalRoot) {
      return balRoot;
    }

    final Hash computed =
        computeAndCommitRoot.computeRootAndCommit(worldState, stateUpdater, blockHeader, cfg);

    if (!computed.equals(balRoot)) {
      throw new IllegalStateException(
          String.format("BAL root mismatch: computed %s vs BAL %s", computed, balRoot));
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
