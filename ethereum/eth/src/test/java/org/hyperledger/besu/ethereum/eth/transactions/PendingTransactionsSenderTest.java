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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.util.Arrays;
import java.util.Collections;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class PendingTransactionsSenderTest {

  @Test
  public void testSendEth65PeersOnly() {
    PeerPendingTransactionTracker peerPendingTransactionTracker =
        mock(PeerPendingTransactionTracker.class);

    PendingTransactionsMessageSender pendingTransactionsMessageSender =
        mock(PendingTransactionsMessageSender.class);
    EthContext ethContext = mock(EthContext.class);
    EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    PendingTransactionSender sender =
        new PendingTransactionSender(
            peerPendingTransactionTracker, pendingTransactionsMessageSender, ethContext);

    EthPeer peer1 = mock(EthPeer.class);
    EthPeer peer2 = mock(EthPeer.class);
    Transaction tx = mock(Transaction.class);
    Hash hash = Hash.wrap(Bytes32.random());
    when(tx.getHash()).thenReturn(hash);
    EthPeers ethPeers = mock(EthPeers.class);
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethPeers.streamAvailablePeers()).thenReturn(Arrays.asList(peer1, peer2).stream());
    when(peerPendingTransactionTracker.isPeerSupported(peer1)).thenReturn(true);
    when(peerPendingTransactionTracker.isPeerSupported(peer2)).thenReturn(false);
    sender.onTransactionsAdded(Collections.singleton(tx));
    verify(peerPendingTransactionTracker, times(1)).addToPeerSendQueue(peer1, hash);
    verify(peerPendingTransactionTracker, never()).addToPeerSendQueue(peer2, hash);
  }
}
