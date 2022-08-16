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

public class PandaPrinter implements InSyncListener, ForkchoiceMessageListener, MergeStateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(PandaPrinter.class);
  private static final String readyBanner = PandaPrinter.loadBanner("/readyPanda.txt");
  private static final String ttdBanner = PandaPrinter.loadBanner("/ttdPanda.txt");
  private static final String finalizedBanner = PandaPrinter.loadBanner("/finalizedPanda.txt");

  private static final AtomicBoolean hasTTD = new AtomicBoolean(false);
  private static final AtomicBoolean inSync = new AtomicBoolean(false);
  public static final AtomicBoolean readyBeenDisplayed = new AtomicBoolean();
  public static final AtomicBoolean ttdBeenDisplayed = new AtomicBoolean();
  public static final AtomicBoolean finalizedBeenDisplayed = new AtomicBoolean();

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
      LOG.error("Couldn't load hilarious panda banner");
    }
    return resultStringBuilder.toString();
  }

  public static void hasTTD() {
    PandaPrinter.hasTTD.getAndSet(true);
    if (hasTTD.get() && inSync.get()) {
      printReadyToMerge();
    }
  }

  public static void inSync() {
    PandaPrinter.inSync.getAndSet(true);
    if (inSync.get() && hasTTD.get()) {
      printReadyToMerge();
    }
  }

  public static void printOnFirstCrossing() {
    if (!ttdBeenDisplayed.get()) {
      LOG.info("\n" + ttdBanner);
    }
    ttdBeenDisplayed.compareAndSet(false, true);
  }

  static void resetForTesting() {
    ttdBeenDisplayed.set(false);
    readyBeenDisplayed.set(false);
    finalizedBeenDisplayed.set(false);
  }

  public static void printReadyToMerge() {
    if (!readyBeenDisplayed.get()) {
      LOG.info("\n" + readyBanner);
    }
    readyBeenDisplayed.compareAndSet(false, true);
  }

  public static void printFinalized() {
    if (!finalizedBeenDisplayed.get()) {
      LOG.info("\n" + finalizedBanner);
    }
    finalizedBeenDisplayed.compareAndSet(false, true);
  }

  @Override
  public void onInSyncStatusChange(final boolean newSyncStatus) {
    if (newSyncStatus && hasTTD.get()) {
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
  public void mergeStateChanged(
      final boolean isPoS,
      final Optional<Boolean> priorState,
      final Optional<Difficulty> difficultyStoppedAt) {
    if (isPoS && priorState.isPresent() && !priorState.get()) { // just crossed from PoW to PoS
      printOnFirstCrossing();
    }
  }
}
