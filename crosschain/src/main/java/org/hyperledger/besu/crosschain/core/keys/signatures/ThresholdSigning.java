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
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.p2p.CrosschainDevP2PInterface;

public class ThresholdSigning {
  final CrosschainDevP2PInterface p2p;

  // Offset into listofNodes array.
  //  private int nodeNumber;
  //  private ThresholdSigning[] listOfNodes;

  private BlsCryptoProvider cryptoProvider;

  //  private ThresholdScheme thresholdScheme;

  public ThresholdSigning(final CrosschainDevP2PInterface p2p) {
    this.p2p = p2p;
    this.cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
  }

  public BlsPoint sign(final BlsThresholdCredentials credentials, final byte[] data) {
    //    int threshold = credentials.getBlockchainPublicKeyThreshold();
    //
    //    this.thresholdScheme = new ThresholdScheme(this.cryptoProvider, threshold);
    //
    //    // TODO a more complex implementation is needed, which sends to all nodes, and then only
    // uses
    //    // threshold of them
    //    BlsPoint[] sigShares = new BlsPoint[threshold];
    //
    //    for (int i = 0; i < threshold; i++) {
    //      if (i == this.nodeNumber) {
    //        // Sign locally for the Coordinating Node.
    //        sigShares[i] = localSign(credentials, data);
    //      } else {
    //        // Request another node sign the data.
    //        sigShares[i] =
    //            (BlsPoint)
    //                sendPrivateMessage(InterNodeMessages.REQUEST_SIGN, this.listOfNodes[i], data);
    //      }
    //    }
    //
    //    // Add all of the points for each of the x values.
    //    BigInteger[] xValues = this.thresholdContract.getAllNodeIds();
    //
    //    BlsPointSecretShare[] shares = new BlsPointSecretShare[this.threshold];
    //    for (int i = 0; i < this.threshold; i++) {
    //      shares[i] = new BlsPointSecretShare(xValues[i], sigShares[i]);
    //    }
    //
    //    // Do Lagrange interpolation to determine the group public key (the point for x=0).
    //    return this.thresholdScheme.calculateSecret(shares);
    return null;
  }

  //  private BlsPoint localSign(final BlsThresholdCredentials credentials, final byte[] data) {
  //    return this.cryptoProvider.sign(credentials.getPrivateKeyShare(), data);
  //  }

  public boolean verify(
      final BlsThresholdCredentials credentials,
      final byte[] dataToBeVerified,
      final BlsPoint signature) {
    return this.cryptoProvider.verify(credentials.getPublicKey(), dataToBeVerified, signature);
  }
}
