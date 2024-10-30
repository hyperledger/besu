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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RequestManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class PeerTaskRequestSenderTest {

  private PeerTaskRequestSender peerTaskRequestSender;

  @BeforeEach
  public void beforeTest() {
    peerTaskRequestSender = new PeerTaskRequestSender();
  }

  @Test
  public void testSendRequest()
      throws PeerConnection.PeerNotConnected, ExecutionException, InterruptedException {
    SubProtocol subprotocol = Mockito.mock(SubProtocol.class);
    MessageData requestMessageData = Mockito.mock(MessageData.class);
    MessageData responseMessageData = Mockito.mock(MessageData.class);
    EthPeer peer = Mockito.mock(EthPeer.class);
    PeerConnection peerConnection = Mockito.mock(PeerConnection.class);
    RequestManager.ResponseStream responseStream =
        Mockito.mock(RequestManager.ResponseStream.class);

    Mockito.when(peer.getConnection()).thenReturn(peerConnection);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(peer.send(requestMessageData, "subprotocol", peerConnection))
        .thenReturn(responseStream);

    CompletableFuture<MessageData> actualResponseMessageDataFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return peerTaskRequestSender.sendRequest(subprotocol, requestMessageData, peer);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    Thread.sleep(500);
    ArgumentCaptor<RequestManager.ResponseCallback> responseCallbackArgumentCaptor =
        ArgumentCaptor.forClass(RequestManager.ResponseCallback.class);
    Mockito.verify(responseStream).then(responseCallbackArgumentCaptor.capture());
    RequestManager.ResponseCallback responseCallback = responseCallbackArgumentCaptor.getValue();
    responseCallback.exec(false, responseMessageData, peer);

    Assertions.assertSame(responseMessageData, actualResponseMessageDataFuture.get());
  }
}
