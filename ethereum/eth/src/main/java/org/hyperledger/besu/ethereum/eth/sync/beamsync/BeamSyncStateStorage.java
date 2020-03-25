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
package org.hyperledger.besu.ethereum.eth.sync.beamsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.google.common.io.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Supports persisting beam sync state to disk to enable resuming after a restart.
 *
 * <p>Note that a {@link BeamSyncState} with a block number selected but no launch block header is
 * not stored. If we haven't yet retrieved and confirmed the actual block header we can't have
 * started downloading data so should pick a new launch block when resuming. Once we have the launch
 * block header we want to continue with that launch block so the world state downloaded matches up.
 */
public class BeamSyncStateStorage {
  private static final Logger LOG = LogManager.getLogger();
  private static final String LAUNCH_BLOCK_HEADER_FILENAME = "launchBlockHeader.rlp";
  private final File launchBlockHeaderFile;

  public BeamSyncStateStorage(final Path beamSyncDataDir) {
    launchBlockHeaderFile = beamSyncDataDir.resolve(LAUNCH_BLOCK_HEADER_FILENAME).toFile();
  }

  public boolean isBeamSyncInProgress() {
    return launchBlockHeaderFile.isFile();
  }

  public BeamSyncState loadState(final BlockHeaderFunctions blockHeaderFunctions) {
    try {
      if (!isBeamSyncInProgress()) {
        return BeamSyncState.EMPTY_SYNC_STATE;
      }
      final Bytes rlp = Bytes.wrap(Files.toByteArray(launchBlockHeaderFile));
      return new BeamSyncState(
          BlockHeader.readFrom(new BytesValueRLPInput(rlp, false), blockHeaderFunctions));
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to read beam sync status file: " + launchBlockHeaderFile.getAbsolutePath());
    }
  }

  public void storeState(final BeamSyncState state) {
    if (!state.hasLaunchBlockHeader()) {
      if (!launchBlockHeaderFile.delete() && launchBlockHeaderFile.exists()) {
        LOG.error(
            "Unable to delete beam sync status file: " + launchBlockHeaderFile.getAbsolutePath());
      }
      return;
    }
    try {
      final BytesValueRLPOutput output = new BytesValueRLPOutput();
      state.getLaunchBlockHeader().get().writeTo(output);
      Files.write(output.encoded().toArrayUnsafe(), launchBlockHeaderFile);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to store beam sync status file: " + launchBlockHeaderFile.getAbsolutePath());
    }
  }
}
