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
package org.hyperledger.besu.crosschain.core.keys.signatures;

import org.hyperledger.besu.crosschain.core.keys.BlsThresholdCredentials;
import org.hyperledger.besu.crosschain.core.keys.BlsThresholdCryptoSystem;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.BlsPointSecretShare;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.ThresholdScheme;
import org.hyperledger.besu.crosschain.p2p.CrosschainDevP2PInterface;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThresholdSigning {
  protected static final Logger LOG = LogManager.getLogger();

  final BlsThresholdCredentials credentials;
  final CrosschainDevP2PInterface p2p;

  public ThresholdSigning(
      final CrosschainDevP2PInterface p2p, final BlsThresholdCredentials credentials) {
    this.p2p = p2p;
    this.credentials = credentials;
  }

  public BlsPoint sign(final byte[] data, final BytesValue message) {
    int threshold = this.credentials.getThreshold();
    BlsThresholdCryptoSystem cryptoSystem = this.credentials.getAlgorithm();
    BlsCryptoProvider cryptoProvider = cryptoSystem.getCryptoProvider();
    ThresholdScheme thresholdScheme = new ThresholdScheme(cryptoProvider, threshold);

    // If there is only one node, then just locally sign.
    // The result of Lagrange Interpolation for one point is the point. As such, just return the
    // point.
    if (threshold == 1) {
      return localSign(cryptoProvider, data);
    } else {
      Set<BigInteger> otherNodes = this.p2p.getAllPeers();
      if (otherNodes.size() < threshold) {
        String msg =
            "Not enough nodes to threshold sign. Threshold: "
                + threshold
                + ", and number of connected nodes: "
                + otherNodes.size();
        LOG.error(msg);
        throw new Error(msg);
      }

      // TODO register call backs.

      // Send message to all nodes.
      this.p2p.sendMessageSigningRequest(credentials.getMyNodeAddress(), message);

      // TODO a more complex implementation is needed, which sends to all nodes, and then only
      // TODO uses threshold of them.
      BlsPoint[] sigShares = new BlsPoint[threshold];
      sigShares[0] = localSign(cryptoProvider, data);

      // Add all of the points for each of the x values.
      // TODO the x values will be returned in the callback as the address of the node that sent the
      // partial signature share.
      BigInteger[] xValues = null;

      BlsPointSecretShare[] shares = new BlsPointSecretShare[threshold];
      for (int i = 0; i < threshold; i++) {
        shares[i] = new BlsPointSecretShare(xValues[i], sigShares[i]);
      }

      // Do Lagrange interpolation to determine the group public key (the point for x=0).
      return thresholdScheme.calculateSecret(shares);
    }
  }

  private BlsPoint localSign(final BlsCryptoProvider cryptoProvider, final byte[] data) {
    return cryptoProvider.sign(this.credentials.getPrivateKeyShare(), data);
  }

  public boolean verify(final byte[] dataToBeVerified, final BlsPoint signature) {
    BlsThresholdCryptoSystem cryptoSystem = this.credentials.getAlgorithm();
    BlsCryptoProvider cryptoProvider = cryptoSystem.getCryptoProvider();
    return cryptoProvider.verify(this.credentials.getPublicKey(), dataToBeVerified, signature);
  }
}
