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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.google.common.io.Files;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supports persisting fast sync state to disk to enable resuming after a restart.
 *
 * <p>Note that a {@link FastSyncState} with a block number selected but no pivot block header is
 * not stored. If we haven't yet retrieved and confirmed the actual block header we can't have
 * started downloading data so should pick a new pivot block when resuming. Once we have the pivot
 * block header we want to continue with that pivot block so the world state downloaded matches up.
 */
public class FastSyncStateStorage {
  private static final Logger LOG = LoggerFactory.getLogger(FastSyncStateStorage.class);
  private static final String PIVOT_BLOCK_HEADER_FILENAME = "pivotBlockHeader.rlp";
  private final File pivotBlockHeaderFile;

  public FastSyncStateStorage(final Path fastSyncDataDir) {
    pivotBlockHeaderFile = fastSyncDataDir.resolve(PIVOT_BLOCK_HEADER_FILENAME).toFile();
  }

  public boolean isFastSyncInProgress() {
    return pivotBlockHeaderFile.isFile();
  }

  public FastSyncState loadState(final BlockHeaderFunctions blockHeaderFunctions) {
    try {
      if (!isFastSyncInProgress()) {
        return FastSyncState.EMPTY_SYNC_STATE;
      }
      final Bytes rlp = Bytes.wrap(Files.toByteArray(pivotBlockHeaderFile));
      return new FastSyncState(
          BlockHeader.readFrom(new BytesValueRLPInput(rlp, false), blockHeaderFunctions));
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to read fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }

  public void storeState(final FastSyncState state) {
    if (!state.hasPivotBlockHeader()) {
      if (!pivotBlockHeaderFile.delete() && pivotBlockHeaderFile.exists()) {
        LOG.error(
            "Unable to delete fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
      }
      return;
    }
    try {
      final BytesValueRLPOutput output = new BytesValueRLPOutput();
      state.getPivotBlockHeader().get().writeTo(output);
      Files.write(output.encoded().toArrayUnsafe(), pivotBlockHeaderFile);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to store fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }
}
