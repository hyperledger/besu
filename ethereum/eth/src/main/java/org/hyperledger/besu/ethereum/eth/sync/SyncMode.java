/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.sync;

import java.util.EnumSet;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

public enum SyncMode {
  // Fully validate all blocks as they sync
  FULL,
  // Perform light validation on older blocks, and switch to full validation for more recent blocks
  FAST,
  // Perform snapsync
  SNAP,
  // Perform snapsync but starting from a checkpoint instead of starting from genesis
  CHECKPOINT;

  public String normalize() {
    return StringUtils.capitalize(this.toString().toLowerCase(Locale.ROOT));
  }

  public static boolean isFullSync(final SyncMode syncMode) {
    return !EnumSet.of(SyncMode.FAST, SyncMode.SNAP, SyncMode.CHECKPOINT).contains(syncMode);
  }
}
