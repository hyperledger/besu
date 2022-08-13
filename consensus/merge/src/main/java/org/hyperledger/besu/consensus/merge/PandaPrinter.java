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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaPrinter implements InSyncListener, ForkchoiceMessageListener {

  private static final Logger LOG = LoggerFactory.getLogger(PandaPrinter.class);
  private static final String readyBanner = PandaPrinter.loadBanner("/readyPanda.txt");
  private static final String ttdBanner = PandaPrinter.loadBanner("/ttdPanda.txt");
  private static final String finalizedBanner = PandaPrinter.loadBanner("/finalizedPanda.txt");
  private static final AtomicBoolean readyBeenDisplayed = new AtomicBoolean();
  private static final AtomicBoolean ttdBeenDisplayed = new AtomicBoolean();
  private static final AtomicBoolean finalizedBeenDisplayed = new AtomicBoolean();

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

  public static boolean printOnFirstCrossing() {
    boolean shouldPrint = ttdBeenDisplayed.compareAndSet(false, true);
    if (shouldPrint) {
      LOG.info("\n" + ttdBanner);
    }
    return shouldPrint;
  }

  static boolean hasDisplayed() {
    return ttdBeenDisplayed.get();
  }

  static void resetForTesting() {
    ttdBeenDisplayed.set(false);
  }

  public static void printReadyToMerge() {
    boolean shouldPrint = readyBeenDisplayed.compareAndSet(false, true);
    if(shouldPrint) {
      LOG.info("\n"+readyBanner);
    }
  }

  public static void printFinalized() {
    boolean shouldPrint = finalizedBeenDisplayed.compareAndSet(false, true);
    if(shouldPrint) {
      LOG.info("\n"+finalizedBanner);
    }
  }


  @Override
  public void onInSyncStatusChange(boolean newSyncStatus) {
    if(newSyncStatus) {
      printReadyToMerge();
    }
  }

  @Override
  public void onNewForkchoiceMessage(Hash headBlockHash,
      Optional<Hash> maybeFinalizedBlockHash, Hash safeBlockHash) {

  }
}
