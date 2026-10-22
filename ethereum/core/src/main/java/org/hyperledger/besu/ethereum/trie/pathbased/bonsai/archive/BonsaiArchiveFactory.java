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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveTrieNodeStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.FinalizedTrieNodeArchiver;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the Bonsai archive's background components so the controller builder need not. */
public final class BonsaiArchiveFactory {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveFactory.class);

  private BonsaiArchiveFactory() {}

  private static ThreadFactory namedThreads(final String name) {
    return new ThreadFactoryBuilder().setNameFormat(name + "-%d").build();
  }

  /**
   * Builds the Bonsai archive components for {@code worldStateArchive}. Must be called before the
   * genesis state is written: when archive proofs are enabled it installs the trie-node capture
   * strategy so block 0 is captured.
   *
   * <p>Only call this for the {@code X_BONSAI_ARCHIVE} data storage format. The returned handle
   * owns the executors it creates.
   *
   * @param dataStorageConfiguration the data storage configuration
   * @param provider the Bonsai archive world state provider
   * @param worldStateStorageCoordinator the world state storage coordinator
   * @param blockchain the blockchain
   * @param metricsSystem the metrics system
   * @param captureParallelism threads in each trie-node capture pool
   * @return the archive lifecycle handle
   */
  public static BonsaiArchiveService create(
      final DataStorageConfiguration dataStorageConfiguration,
      final BonsaiArchiveWorldStateProvider provider,
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final Blockchain blockchain,
      final MetricsSystem metricsSystem,
      final int captureParallelism) {
    final ExtraStorageConfiguration extraStorage =
        dataStorageConfiguration.getExtraStorageConfiguration();
    final ExtraStorageConfiguration.Unstable unstable = extraStorage.getUnstable();
    final BonsaiWorldStateKeyValueStorage worldStateStorage =
        worldStateStorageCoordinator.getStrategy(BonsaiWorldStateKeyValueStorage.class);

    final List<ExecutorService> executors = new ArrayList<>();
    final ScheduledExecutorService migrationExecutor =
        Executors.newScheduledThreadPool(1, namedThreads("archive-migrator"));
    executors.add(migrationExecutor);

    Optional<BonsaiArchiveService.ProofCapture> proofCapture = Optional.empty();
    if (unstable.getBonsaiArchiveStateProofsEnabled()) {
      final ExecutorService trieCapturePool =
          Executors.newFixedThreadPool(captureParallelism, namedThreads("trie-capture"));
      final ScheduledExecutorService archiverExecutor =
          Executors.newScheduledThreadPool(1, namedThreads("finalized-trienode-archiver"));
      final ExecutorService archiverCapturePool =
          Executors.newFixedThreadPool(captureParallelism, namedThreads("trie-capture-finalized"));
      executors.addAll(List.of(trieCapturePool, archiverExecutor, archiverCapturePool));
      final ArchiveTrieNodeStrategy strategy =
          ArchiveTrieNodeStrategy.createArchiveStrategy(
              worldStateStorage.getComposedWorldStateStorage(),
              trieCapturePool,
              unstable.getBonsaiArchiveShallowCheckpointInterval(),
              unstable.getBonsaiArchiveDeepCheckpointInterval());
      worldStateStorage.setTrieNodeStrategy(strategy);
      LOG.info("Bonsai archive proofs enabled (--Xbonsai-archive-state-proofs-enabled)");
      proofCapture =
          Optional.of(
              new BonsaiArchiveService.ProofCapture(
                  strategy,
                  new FinalizedTrieNodeArchiver(
                      worldStateStorage,
                      provider,
                      provider.getTrieLogManager(),
                      blockchain,
                      archiverExecutor,
                      archiverCapturePool,
                      unstable.getBonsaiArchiveShallowCheckpointInterval(),
                      unstable.getBonsaiArchiveDeepCheckpointInterval())));
    }

    final BonsaiFlatDbToArchiveMigrator migrator =
        new BonsaiFlatDbToArchiveMigrator(
            worldStateStorage,
            provider.getTrieLogManager(),
            blockchain,
            migrationExecutor,
            metricsSystem,
            new BonsaiArchiveFlatDbStrategy(
                metricsSystem, worldStateStorage.getFlatDbStrategy().getCodeStorageStrategy()));
    provider.setArchiveMigrationProgressSupplier(migrator::getMigratedBlockNumber);

    return new BonsaiArchiveService(
        worldStateStorage,
        blockchain,
        migrator,
        extraStorage.getMaxLayersToLoad(),
        proofCapture,
        executors);
  }
}
