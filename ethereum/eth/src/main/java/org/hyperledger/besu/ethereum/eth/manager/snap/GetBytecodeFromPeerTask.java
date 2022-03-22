/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import static java.util.Collections.emptyMap;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerRequestTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;

public class GetBytecodeFromPeerTask extends AbstractPeerRequestTask<Map<Bytes32, Bytes>> {

  private static final Logger LOG = getLogger(GetBytecodeFromPeerTask.class);

  private final List<Bytes32> codeHashes;
  private final BlockHeader blockHeader;

  private GetBytecodeFromPeerTask(
      final EthContext ethContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, SnapV1.STORAGE_RANGE, metricsSystem);
    this.codeHashes = codeHashes;
    this.blockHeader = blockHeader;
  }

  public static GetBytecodeFromPeerTask forBytecode(
      final EthContext ethContext,
      final List<Bytes32> codeHashes,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new GetBytecodeFromPeerTask(ethContext, codeHashes, blockHeader, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        peer -> {
          LOG.trace("Requesting {} Bytecodes from {} .", codeHashes.size(), peer);
          return peer.getSnapBytecode(blockHeader.getStateRoot(), codeHashes);
        },
        blockHeader.getNumber());
  }

  @Override
  protected Optional<Map<Bytes32, Bytes>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {

    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.of(emptyMap());
    }
    final ByteCodesMessage byteCodesMessage = ByteCodesMessage.readFrom(message);
    final ArrayDeque<Bytes> bytecodes = byteCodesMessage.bytecodes(true).codes();
    if (bytecodes.size() > codeHashes.size()) {
      // Can't be the response to our request
      return Optional.empty();
    }
    return mapCodeByHash(bytecodes);
  }

  private Optional<Map<Bytes32, Bytes>> mapCodeByHash(final ArrayDeque<Bytes> bytecodes) {
    final Map<Bytes32, Bytes> codeByHash = new HashMap<>();
    for (int i = 0; i < bytecodes.size(); i++) {
      final Hash hash = Hash.hash(bytecodes.get(i));
      if (codeHashes.get(i).equals(hash)) {
        codeByHash.put(hash, bytecodes.get(i));
      }
    }
    return Optional.of(codeByHash);
  }
}
