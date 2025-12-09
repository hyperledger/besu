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
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.storage.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfigImpl;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.services.storage.WorldStateConfig;
import org.hyperledger.besu.plugin.services.storage.WorldStateKeyValueStorage;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hyperledger.besu.plugin.services.storage.StateRootCommitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"rawtypes", "unchecked"})
final class StateRootCommitterImplBal implements StateRootCommitter {

  private static final Logger LOG = LoggerFactory.getLogger(StateRootCommitterImplBal.class);

  private final CompletableFuture<BalRootComputation> balRootFuture;
  private final BalConfiguration balConfiguration;
  private final StateRootCommitterImplSync syncCommitter = new StateRootCommitterImplSync();

  StateRootCommitterImplBal(
      final CompletableFuture<BalRootComputation> balRootFuture,
      final BalConfiguration balConfiguration) {
    this.balRootFuture = balRootFuture;
    this.balConfiguration = balConfiguration;
  }

  @Override
  public Hash computeRootAndCommit(
      final MutableWorldState worldState,
      final WorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg) {

    final Duration balRootTimeout = balConfiguration.getBalStateRootTimeout();

    if (balConfiguration.isBalStateRootTrusted()) {
      return computeWithTrustedBalRoot(
          (PathBasedWorldState) worldState,
          (PathBasedWorldStateKeyValueStorage.Updater) stateUpdater,
          blockHeader,
          balRootTimeout);
    }

    return computeWithBalVerification(worldState, stateUpdater, blockHeader, cfg, balRootTimeout);
  }

  @Override
  public void cancel() {
    balRootFuture.cancel(true);
  }

  private Hash computeWithTrustedBalRoot(
      final PathBasedWorldState worldState,
      final PathBasedWorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final Duration balRootTimeout) {

    final BalRootComputation balComputation = waitForBalRootStrict(balRootTimeout);
    final Hash balStateRoot = balComputation.root();

    if (!blockHeader.getStateRoot().equals(balStateRoot)) {
      throw new IllegalStateException("BAL-computed root does not match block header state root");
    }
    importBalStateChanges(worldState, stateUpdater, balComputation);

    return balStateRoot;
  }

  private Hash computeWithBalVerification(
      final MutableWorldState worldState,
      final WorldStateKeyValueStorage.Updater stateUpdater,
      final BlockHeader blockHeader,
      final WorldStateConfig cfg,
      final Duration balRootTimeout) {

    final Hash computedRoot =
        syncCommitter.computeRootAndCommit(worldState, stateUpdater, blockHeader, cfg);

    if (balConfiguration.isBalLenientOnStateRootMismatch()) {
      return handleBalLenientMode(computedRoot, balRootTimeout);
    } else {
      return handleBalStrictMode(computedRoot, balRootTimeout);
    }
  }

  private Hash handleBalLenientMode(final Hash computedRoot, final Duration balRootTimeout) {
    final Optional<BalRootComputation> maybeBalRoot = waitForBalRootLenient(balRootTimeout);

    if (maybeBalRoot.isEmpty()) {
      LOG.warn(
          "BAL root unavailable (lenient mode); proceeding with computed state root {}",
          computedRoot);
      return computedRoot;
    }

    final Hash balRoot = maybeBalRoot.get().root();
    if (!computedRoot.equals(balRoot)) {
      LOG.error("BAL root mismatch: computed {} vs BAL {}", computedRoot, balRoot);
    }

    return computedRoot;
  }

  private Hash handleBalStrictMode(final Hash computedRoot, final Duration balRootTimeout) {
    final Hash balRoot = waitForBalRootStrict(balRootTimeout).root();

    if (!computedRoot.equals(balRoot)) {
      final String errorMessage =
          String.format("BAL root mismatch: computed %s vs BAL %s", computedRoot, balRoot);
      throw new IllegalStateException(errorMessage);
    }

    return computedRoot;
  }

  private void importBalStateChanges(
      final PathBasedWorldState worldState,
      final PathBasedWorldStateKeyValueStorage.Updater stateUpdater,
      final BalRootComputation balComputation) {

    final PathBasedWorldStateUpdateAccumulator balAccumulator = balComputation.accumulator();
    final PathBasedWorldStateUpdateAccumulator blockAccumulator = worldState.updater();

    blockAccumulator.importStateChangesFromSource(balAccumulator);

    final PathBasedLayeredWorldStateKeyValueStorage balStateUpdater =
        (PathBasedLayeredWorldStateKeyValueStorage) balAccumulator.getWorldStateStorage();
    if (!worldState.isStorageFrozen()) {
      balStateUpdater.mergeTo(stateUpdater.getWorldStateTransaction());
    }
  }

  private BalRootComputation waitForBalRootStrict(final Duration balRootTimeout) {
    try {
      return getWithConfiguredTimeout(balRootTimeout);
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

  private Optional<BalRootComputation> waitForBalRootLenient(final Duration balRootTimeout) {
    try {
      return Optional.of(getWithConfiguredTimeout(balRootTimeout));
    } catch (final TimeoutException e) {
      balRootFuture.cancel(true);
      LOG.warn(
          "Timed out waiting {} for BAL state root (lenient mode); proceeding.", balRootTimeout);
      return Optional.empty();
    } catch (final ExecutionException e) {
      LOG.warn("Failed to compute BAL state root (lenient mode); proceeding.", e.getCause());
      return Optional.empty();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for BAL state root (lenient mode); proceeding.");
      return Optional.empty();
    }
  }

  private BalRootComputation getWithConfiguredTimeout(final Duration balRootTimeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (balRootTimeout.isNegative()) {
      return balRootFuture.join();
    }
    return balRootFuture.get(balRootTimeout.toNanos(), TimeUnit.NANOSECONDS);
  }
}
