/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.peervalidation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

public class PeerValidatorRunnerTest {

  @Test
  public void checkPeer_schedulesFutureCheckWhenPeerNotReady() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager).getEthPeer();

    PeerValidator validator = mock(PeerValidator.class);
    when(validator.canBeValidated(eq(peer))).thenReturn(false);
    when(validator.nextValidationCheckTimeout(eq(peer))).thenReturn(Duration.ofSeconds(30));

    PeerValidatorRunner runner =
        spy(new PeerValidatorRunner(ethProtocolManager.ethContext(), validator));
    runner.checkPeer(peer);

    verify(runner, times(1)).checkPeer(eq(peer));
    verify(validator, never()).validatePeer(eq(peer));
    verify(runner, never()).disconnectPeer(eq(peer));
    verify(runner, times(1)).scheduleNextCheck(eq(peer));

    // Run pending futures to trigger the next check
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    verify(runner, times(2)).checkPeer(eq(peer));
    verify(validator, never()).validatePeer(eq(peer));
    verify(runner, never()).disconnectPeer(eq(peer));
    verify(runner, times(2)).scheduleNextCheck(eq(peer));
  }

  @Test
  public void checkPeer_doesNotScheduleFutureCheckWhenPeerNotReadyAndDisconnected() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager).getEthPeer();
    peer.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);

    PeerValidator validator = mock(PeerValidator.class);
    when(validator.canBeValidated(eq(peer))).thenReturn(false);
    when(validator.nextValidationCheckTimeout(eq(peer))).thenReturn(Duration.ofSeconds(30));

    PeerValidatorRunner runner =
        spy(new PeerValidatorRunner(ethProtocolManager.ethContext(), validator));
    runner.checkPeer(peer);

    verify(runner, times(1)).checkPeer(eq(peer));
    verify(validator, never()).validatePeer(eq(peer));
    verify(runner, never()).disconnectPeer(eq(peer));
    verify(runner, times(0)).scheduleNextCheck(eq(peer));
  }

  @Test
  public void checkPeer_handlesInvalidPeer() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager).getEthPeer();

    PeerValidator validator = mock(PeerValidator.class);
    when(validator.canBeValidated(eq(peer))).thenReturn(true);
    when(validator.validatePeer(eq(peer))).thenReturn(CompletableFuture.completedFuture(false));
    when(validator.nextValidationCheckTimeout(eq(peer))).thenReturn(Duration.ofSeconds(30));

    PeerValidatorRunner runner =
        spy(new PeerValidatorRunner(ethProtocolManager.ethContext(), validator));
    runner.checkPeer(peer);

    verify(validator, times(1)).validatePeer(eq(peer));
    verify(runner, times(1)).disconnectPeer(eq(peer));
    verify(runner, never()).scheduleNextCheck(eq(peer));
  }

  @Test
  public void checkPeer_handlesValidPeer() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager).getEthPeer();

    PeerValidator validator = mock(PeerValidator.class);
    when(validator.canBeValidated(eq(peer))).thenReturn(true);
    when(validator.validatePeer(eq(peer))).thenReturn(CompletableFuture.completedFuture(true));
    when(validator.nextValidationCheckTimeout(eq(peer))).thenReturn(Duration.ofSeconds(30));

    PeerValidatorRunner runner =
        spy(new PeerValidatorRunner(ethProtocolManager.ethContext(), validator));
    runner.checkPeer(peer);

    verify(validator, times(1)).validatePeer(eq(peer));
    verify(runner, never()).disconnectPeer(eq(peer));
    verify(runner, never()).scheduleNextCheck(eq(peer));
  }
}
