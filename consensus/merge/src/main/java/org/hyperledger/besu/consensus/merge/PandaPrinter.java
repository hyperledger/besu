/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaPrinter implements InSyncListener, ForkchoiceMessageListener, BlockAddedObserver {

  private static PandaPrinter INSTANCE;

  public static PandaPrinter init(final Optional<Difficulty> currentTotal, final Difficulty ttd) {
    if (INSTANCE != null) {
      LOG.debug("overwriting already initialized panda printer");
    }

    INSTANCE = new PandaPrinter(currentTotal, ttd);

    return INSTANCE;
  }

  public static PandaPrinter getInstance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("Uninitialized, unknown ttd");
    }
    return INSTANCE;
  }

  protected PandaPrinter(final Optional<Difficulty> currentTotal, final Difficulty ttd) {
    this.ttd = ttd;
    if (currentTotal.isPresent() && currentTotal.get().greaterOrEqualThan(ttd)) {
      this.readyBeenDisplayed.set(true);
      this.ttdBeenDisplayed.set(true);
      this.finalizedBeenDisplayed.set(true);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(PandaPrinter.class);
  private static final String readyBanner = PandaPrinter.loadBanner("/readyPanda.txt");
  private static final String ttdBanner = PandaPrinter.loadBanner("/ttdPanda.txt");
  private static final String finalizedBanner = PandaPrinter.loadBanner("/finalizedPanda.txt");

  private final Difficulty ttd;
  private final AtomicBoolean hasTTD = new AtomicBoolean(false);
  private final AtomicBoolean inSync = new AtomicBoolean(false);
  private final AtomicBoolean isPoS = new AtomicBoolean(false);

  public final AtomicBoolean readyBeenDisplayed = new AtomicBoolean();
  public final AtomicBoolean ttdBeenDisplayed = new AtomicBoolean();
  public final AtomicBoolean finalizedBeenDisplayed = new AtomicBoolean();

  private static String loadBanner(final String filename) {
    Class<PandaPrinter> c = PandaPrinter.class;
    InputStream is = c.getResourceAsStream(filename);
    StringBuilder resultStringBuilder = new StringBuilder();
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        resultStringBuilder.append(line).append("\n");
      }
    } catch (IOException e) {
      LOG.error("Couldn't load hilarious panda banner at {} ", filename);
    }
    return resultStringBuilder.toString();
  }

  public void hasTTD() {
    this.hasTTD.getAndSet(true);
  }

  public void inSync() {
    this.inSync.getAndSet(true);
  }

  public void printOnFirstCrossing() {
    if (!ttdBeenDisplayed.get()) {
      LOG.info("Crossed TTD, merging underway!");
      LOG.info("\n" + ttdBanner);
      ttdBeenDisplayed.compareAndSet(false, true);
    }
  }

  public void resetForTesting() {
    ttdBeenDisplayed.set(false);
    readyBeenDisplayed.set(false);
    finalizedBeenDisplayed.set(false);
    hasTTD.set(false);
    isPoS.set(false);
    inSync.set(false);
  }

  public void printReadyToMerge() {
    if (!readyBeenDisplayed.get() && !isPoS.get() && inSync.get()) {
      LOG.info("Configured for TTD and in sync, still receiving PoW blocks. Ready to merge!");
      LOG.info("\n" + readyBanner);
      readyBeenDisplayed.compareAndSet(false, true);
    }
  }

  public void printFinalized() {
    if (!finalizedBeenDisplayed.get()) {
      LOG.info("Beacon chain finalized, welcome to Proof of Stake Ethereum");
      LOG.info("\n" + finalizedBanner);
      finalizedBeenDisplayed.compareAndSet(false, true);
    }
  }

  @Override
  public void onInSyncStatusChange(final boolean newSyncStatus) {
    inSync.set(newSyncStatus);
    if (newSyncStatus && hasTTD.get() && !isPoS.get()) {
      printReadyToMerge();
    }
  }

  @Override
  public void onNewForkchoiceMessage(
      final Hash headBlockHash,
      final Optional<Hash> maybeFinalizedBlockHash,
      final Hash safeBlockHash) {
    if (maybeFinalizedBlockHash.isPresent() && !maybeFinalizedBlockHash.get().equals(Hash.ZERO)) {
      printFinalized();
    }
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      Block added = event.getBlock();
      if (added.getHeader().getDifficulty().greaterOrEqualThan(this.ttd)) {
        this.isPoS.set(true);
        if (this.inSync.get()) {
          this.printOnFirstCrossing();
        }
      } else {
        this.isPoS.set(false);
        if (this.inSync.get()) {
          if (!readyBeenDisplayed.get()) {
            this.printReadyToMerge();
          }
        }
      }
    }
  }
}
