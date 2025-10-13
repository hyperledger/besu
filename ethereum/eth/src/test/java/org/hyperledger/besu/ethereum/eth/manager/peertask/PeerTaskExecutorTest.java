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
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;
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
  private @Mock PeerSelector peerSelector;
  private @Mock PeerTaskRequestSender requestSender;
  private @Mock PeerTask<Object> peerTask;
  private @Mock SubProtocol subprotocol;
  private @Mock MessageData requestMessageData;
  private @Mock MessageData responseMessageData;
  private @Mock EthPeer ethPeer;
  private AutoCloseable mockCloser;

  private PeerTaskExecutor peerTaskExecutor;

  @BeforeEach
  public void beforeTest() {
    mockCloser = MockitoAnnotations.openMocks(this);
    peerTaskExecutor = new PeerTaskExecutor(peerSelector, requestSender, new NoOpMetricsSystem());
  }

  @AfterEach
  public void afterTest() throws Exception {
    mockCloser.close();
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndSuccessfulFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {

    Object responseObject = new Object();

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertSame(responseObject, result.result().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPeerShouldBeDisconnected()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {

    Object responseObject = new Object();

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.NON_SEQUENTIAL_HEADERS_RETURNED);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer)
        .disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_NON_SEQUENTIAL_HEADERS);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertSame(responseObject, result.result().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPartialSuccessfulFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {

    Object responseObject = new Object();

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.NO_RESULTS_RETURNED);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithRetriesAndSuccessfulFlowAfterFirstFailure()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    Object responseObject = new Object();
    int requestMessageDataCode = 123;

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(2);

    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException())
        .thenReturn(responseMessageData);
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);
    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertSame(responseObject, result.result().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndPeerNotConnected()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new PeerConnection.PeerNotConnected(""));

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isEmpty());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.PEER_DISCONNECTED, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndTimeoutException()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {
    int requestMessageDataCode = 123;

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException());
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isEmpty());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.TIMEOUT, result.responseCode());
  }

  @Test
  public void testExecuteAgainstPeerWithNoRetriesAndInvalidResponseMessage()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData))
        .thenThrow(new InvalidPeerTaskResponseException());

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUselessResponse(null);

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isEmpty());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.INVALID_RESPONSE, result.responseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithNoRetriesAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    Object responseObject = new Object();

    Mockito.when(peerSelector.getPeer(Mockito.any(Predicate.class)))
        .thenReturn(Optional.of(ethPeer));

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithOtherPeer()).thenReturn(0);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(subprotocol.getName()).thenReturn("subprotocol");
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.executeAgainstPeer(peerTask, ethPeer);

    Mockito.verify(ethPeer).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertSame(responseObject, result.result().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testExecuteWithPeerSwitchingAndSuccessFlow()
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException,
          InvalidPeerTaskResponseException {
    Object responseObject = new Object();
    int requestMessageDataCode = 123;
    EthPeer peer2 = Mockito.mock(EthPeer.class);

    Mockito.when(peerSelector.getPeer(Mockito.any(Predicate.class)))
        .thenReturn(Optional.of(ethPeer))
        .thenReturn(Optional.of(peer2));

    Mockito.when(peerTask.getRequestMessage()).thenReturn(requestMessageData);
    Mockito.when(peerTask.getRetriesWithOtherPeer()).thenReturn(2);
    Mockito.when(peerTask.getRetriesWithSamePeer()).thenReturn(0);
    Mockito.when(peerTask.getSubProtocol()).thenReturn(subprotocol);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, ethPeer))
        .thenThrow(new TimeoutException());
    Mockito.when(requestMessageData.getCode()).thenReturn(requestMessageDataCode);
    Mockito.when(requestSender.sendRequest(subprotocol, requestMessageData, peer2))
        .thenReturn(responseMessageData);
    Mockito.when(peerTask.processResponse(responseMessageData)).thenReturn(responseObject);
    Mockito.when(peerTask.validateResult(responseObject))
        .thenReturn(PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD);

    PeerTaskExecutorResult<Object> result = peerTaskExecutor.execute(peerTask);

    Mockito.verify(ethPeer).recordRequestTimeout(requestMessageDataCode);
    Mockito.verify(peer2).recordUsefulResponse();

    Assertions.assertNotNull(result);
    Assertions.assertTrue(result.result().isPresent());
    Assertions.assertSame(responseObject, result.result().get());
    Assertions.assertEquals(PeerTaskExecutorResponseCode.SUCCESS, result.responseCode());
  }
}
