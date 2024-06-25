/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.PendingPeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.RequestManager;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerRequestTask;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAccountRangeFromPeerTask
    extends AbstractPeerRequestTask<AccountRangeMessage.AccountRangeData> {

  private static final Logger LOG = LoggerFactory.getLogger(GetAccountRangeFromPeerTask.class);

  private final Bytes32 startKeyHash;
  private final Bytes32 endKeyHash;
  private final BlockHeader blockHeader;

  private GetAccountRangeFromPeerTask(
      final EthContext ethContext,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    super(ethContext, SnapV1.ACCOUNT_RANGE, metricsSystem);
    this.startKeyHash = startKeyHash;
    this.endKeyHash = endKeyHash;
    this.blockHeader = blockHeader;
  }

  public static GetAccountRangeFromPeerTask forAccountRange(
      final EthContext ethContext,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final BlockHeader blockHeader,
      final MetricsSystem metricsSystem) {
    return new GetAccountRangeFromPeerTask(
        ethContext, startKeyHash, endKeyHash, blockHeader, metricsSystem);
  }

  @Override
  protected PendingPeerRequest sendRequest() {
    return sendRequestToPeer(
        new PeerRequest() {
          @Override
          public RequestManager.ResponseStream sendRequest(final EthPeer peer)
              throws PeerConnection.PeerNotConnected {
            LOG.atTrace()
                .setMessage("Requesting account range [{} ,{}] for state root {} from peer {} .")
                .addArgument(startKeyHash)
                .addArgument(endKeyHash)
                .addArgument(blockHeader)
                .addArgument(peer)
                .log();
            if (!peer.isServingSnap()) {
              LOG.atDebug()
                  .setMessage("EthPeer that is not serving snap called in {}, peer: {}")
                  .addArgument(GetAccountRangeFromPeerTask.class)
                  .addArgument(peer)
                  .log();
              throw new RuntimeException(
                  "EthPeer that is not serving snap called in "
                      + GetAccountRangeFromPeerTask.class);
            }
            return peer.getSnapAccountRange(blockHeader.getStateRoot(), startKeyHash, endKeyHash);
          }

          @Override
          public boolean isEthPeerSuitable(final EthPeer ethPeer) {
            return ethPeer.isServingSnap();
          }
        },
        blockHeader.getNumber());
  }

  @Override
  protected Optional<AccountRangeMessage.AccountRangeData> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {

    if (streamClosed) {
      // We don't record this as a useless response because it's impossible to know if a peer has
      // the data we're requesting.
      return Optional.empty();
    }
    final AccountRangeMessage accountRangeMessage = AccountRangeMessage.readFrom(message);
    final AccountRangeMessage.AccountRangeData accountRangeData =
        accountRangeMessage.accountData(true);
    return Optional.of(accountRangeData);
  }
}
