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
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class PeerTaskExecutorTest {
  private @Mock PeerManager peerManager;
  private @Mock PeerTaskRequestSender requestSender;
  private @Mock ProtocolSpec protocolSpec;
  private @Mock PeerTask<Object> peerTask;
  private @Mock MessageData requestMessageData;
  private @Mock MessageData responseMessageData;
  private @Mock EthPeer ethPeer;
  private AutoCloseable mockCloser;

  private PeerTaskExecutor peerTaskExecutor;

  @BeforeEach
  public void beforeTest() {
    mockCloser = MockitoAnnotations.openMocks(this);
    peerTaskExecutor =
        new PeerTaskExecutor(
            peerManager, requestSender, () -> protocolSpec, new NoOpMetricsSystem());
  }

  @AfterEach
  public void afterTest() throws Exception {
    mockCloser.close();
  }

  @Test
  public void testExecuteAgainstPeerWithNoPeerTaskBehaviorsAndSuccessfulFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    String subprotocol = "subprotocol";
    Object responseObject = new Object();

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors()).thenReturn(Collections.emptyList());
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.parseResponse(responseMessageData)).thenReturn(responseObject);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isPresent());
    Assertions.assertSame(responseObject, result.getResult().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.getResponseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithRetryBehaviorsAndSuccessfulFlowAfterFirstFailure()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    String subprotocol = "subprotocol";
    Object responseObject = new Object();
    int requestMessageDataCode = 123;

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors())
        .thenReturn(List.of(PeerTaskBehavior.RETRY_WITH_SAME_PEER));

    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException())
        .thenReturn(responseMessageData);
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    Mockito.when(peerTask.parseResponse(responseMessageData)).thenReturn(responseObject);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);
    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isPresent());
    Assertions.assertSame(responseObject, result.getResult().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.getResponseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoPeerTaskBehaviorsAndPeerNotConnected()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    String subprotocol = "subprotocol";

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors()).thenReturn(Collections.emptyList());
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new PeerConnection.PeerNotConnected(""));

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isEmpty());
    Assertions.assertEquals(
        PeerTaskExecutorResponseCode.PEER_DISCONNECTED, result.getResponseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoPeerTaskBehaviorsAndTimeoutException()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    String subprotocol = "subprotocol";
    int requestMessageDataCode = 123;

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors()).thenReturn(Collections.emptyList());
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException());
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isEmpty());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.TIMEOUT, result.getResponseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoPeerTaskBehaviorsAndInvalidResponseMessage()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    String subprotocol = "subprotocol";

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors()).thenReturn(Collections.emptyList());
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.parseResponse(responseMessageData))
        .thenThrow(new InvalidPeerTaskResponseException());

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUselessResponse(null);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isEmpty());
    Assertions.assertEquals(
        PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.getResponseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithNoPeerTaskBehaviorsAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          NoAvailablePeerException {
    String subprotocol = "subprotocol";
    Object responseObject = new Object();

    Mockito.when(peerManager.getPeer(Mockito.any(Predicate.class))).thenReturn(ethPeer);

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors()).thenReturn(Collections.emptyList());
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.parseResponse(responseMessageData)).thenReturn(responseObject);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isPresent());
    Assertions.assertSame(responseObject, result.getResult().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.getResponseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithPeerSwitchingAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException,
          NoAvailablePeerException {
    String subprotocol = "subprotocol";
    Object responseObject = new Object();
    int requestMessageDataCode = 123;
    EthPeer peer2 = Mockito.mock(EthPeer.class);

    Mockito.when(peerManager.getPeer(Mockito.any(Predicate.class)))
        .thenReturn(ethPeer)
        .thenReturn(peer2);

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getPeerTaskBehaviors())
        .thenReturn(List.of(PeerTaskBehavior.RETRY_WITH_OTHER_PEERS));
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException());
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, peer2))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.parseResponse(responseMessageData)).thenReturn(responseObject);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.execute(peerTask);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);
    Mockito.verify(peer2).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.getResult().isPresent());
    Assertions.assertSame(responseObject, result.getResult().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.getResponseCode());
  }
}
