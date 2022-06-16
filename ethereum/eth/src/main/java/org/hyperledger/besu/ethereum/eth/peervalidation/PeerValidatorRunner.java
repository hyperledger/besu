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
package org.hyperledger.besu.ethereum.eth.peervalidation;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerValidatorRunner {
  private static final Logger LOG = LoggerFactory.getLogger(PeerValidatorRunner.class);
  protected final EthContext ethContext;
  private final PeerValidator peerValidator;

  PeerValidatorRunner(final EthContext ethContext, final PeerValidator peerValidator) {
    this.ethContext = ethContext;
    this.peerValidator = peerValidator;

    ethContext.getEthPeers().subscribeConnect(this::checkPeer);
  }

  public static void runValidator(final EthContext ethContext, final PeerValidator peerValidator) {
    new PeerValidatorRunner(ethContext, peerValidator);
  }

  public void checkPeer(final EthPeer ethPeer) {
    if (peerValidator.canBeValidated(ethPeer)) {
      peerValidator
          .validatePeer(ethContext, ethPeer)
          .whenComplete(
              (validated, err) -> {
                if (err != null || !validated) {
                  // Disconnect invalid peer
                  disconnectPeer(ethPeer);
                } else {
                  ethPeer.markValidated(peerValidator);
                }
              });
    } else if (!ethPeer.isDisconnected()) {
      scheduleNextCheck(ethPeer);
    }
  }

  protected void disconnectPeer(final EthPeer ethPeer) {
    LOG.debug(
        "Disconnecting from peer {} marked invalid by {}",
        ethPeer,
        peerValidator.getClass().getSimpleName());
    ethPeer.disconnect(peerValidator.getDisconnectReason(ethPeer));
  }

  protected void scheduleNextCheck(final EthPeer ethPeer) {
    Duration timeout = peerValidator.nextValidationCheckTimeout(ethPeer);
    ethContext.getScheduler().scheduleFutureTask(() -> checkPeer(ethPeer), timeout);
  }
}
