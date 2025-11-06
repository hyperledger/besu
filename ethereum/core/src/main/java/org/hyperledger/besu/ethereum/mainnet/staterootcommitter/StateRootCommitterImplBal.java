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
import java.util.Optional;
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
  private final StateRootCommitterImplSync computeAndCommitRoot = new StateRootCommitterImplSync();

  StateRootCommitterImplBal(
      final CompletableFuture<Hash> balRootFuture, final BalConfiguration balConfiguration) {
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
      return waitForBalRootStrict(balRootTimeout);
    }

    final Hash computed =
        computeAndCommitRoot.computeRootAndCommit(worldState, stateUpdater, blockHeader, cfg);

    if (balConfiguration.isBalLenientOnStateRootMismatch()) {
      final Optional<Hash> maybeBalRoot = waitForBalRootLenient(balRootTimeout);
      if (maybeBalRoot.isEmpty()) {
        LOG.warn(
            "BAL root unavailable (lenient mode); proceeding with computed state root {}",
            computed);
        return computed;
      }

      final Hash balRoot = maybeBalRoot.get();
      if (!computed.equals(balRoot)) {
        LOG.error("BAL root mismatch: computed {} vs BAL {}", computed, balRoot);
      }
      return computed;
    } else {
      final Hash balRoot = waitForBalRootStrict(balRootTimeout);
      if (!computed.equals(balRoot)) {
        final String msg =
            String.format("BAL root mismatch: computed %s vs BAL %s", computed, balRoot);
        throw new IllegalStateException(msg);
      }
      return computed;
    }
  }

  @Override
  public void cancel() {
    balRootFuture.cancel(true);
  }

  private Hash waitForBalRootStrict(final Duration balRootTimeout) {
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

  private Optional<Hash> waitForBalRootLenient(final Duration balRootTimeout) {
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

  private Hash getWithConfiguredTimeout(final Duration balRootTimeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (balRootTimeout.isNegative()) {
      return balRootFuture.join();
    }
    return balRootFuture.get(balRootTimeout.toNanos(), TimeUnit.NANOSECONDS);
  }
}
