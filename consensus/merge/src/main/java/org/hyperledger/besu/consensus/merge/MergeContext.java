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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;

public interface MergeContext extends ConsensusContext {

  MergeContext setSyncState(SyncState syncState);

  MergeContext setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty);

  void setIsPostMerge(final Difficulty totalDifficulty);

  boolean isPostMerge();

  boolean isSyncing();

  void observeNewIsPostMergeState(final NewMergeStateCallback newMergeStateCallback);

  Difficulty getTerminalTotalDifficulty();

  void setFinalized(final BlockHeader blockHeader);

  Optional<BlockHeader> getFinalized();

  boolean validateCandidateHead(final BlockHeader candidateHeader);

  void putPayloadById(final PayloadIdentifier payloadId, final Block block);

  void replacePayloadById(final PayloadIdentifier payloadId, final Block block);

  Optional<Block> retrieveBlockById(final PayloadIdentifier payloadId);

  interface NewMergeStateCallback {
    void onNewIsPostMergeState(final boolean newIsPostMergeState);
  }
}
