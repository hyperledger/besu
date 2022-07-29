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
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaPrinter {

  private static final Logger LOG = LoggerFactory.getLogger(PandaPrinter.class);
  private static final String pandaBanner = PandaPrinter.loadBanner();
  private static final AtomicBoolean beenDisplayed = new AtomicBoolean();

  private static String loadBanner() {
    Class<PandaPrinter> c = PandaPrinter.class;
    InputStream is = c.getResourceAsStream("/ProofOfPanda3.txt");
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
    boolean shouldPrint = beenDisplayed.compareAndSet(false, true);
    if (shouldPrint) {
      LOG.info("\n" + pandaBanner);
    }
    return shouldPrint;
  }

  static boolean hasDisplayed() {
    return beenDisplayed.get();
  }

  static void resetForTesting() {
    beenDisplayed.set(false);
  }
}
