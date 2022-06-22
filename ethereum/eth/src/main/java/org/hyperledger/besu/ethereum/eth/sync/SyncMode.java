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

public enum SyncMode {
  // Fully validate all blocks as they sync
  FULL,
  // Perform light validation on older blocks, and switch to full validation for more recent blocks
  FAST,
  // Perform snapsync
  X_SNAP,
  // Perform snapsync but starting from a checkpoint instead of starting from genesis
  X_CHECKPOINT;

  public static boolean isFullSync(final SyncMode syncMode) {
    return !EnumSet.of(SyncMode.FAST, SyncMode.X_SNAP, SyncMode.X_CHECKPOINT).contains(syncMode);
  }
}
