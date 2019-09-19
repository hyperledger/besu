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
package org.hyperledger.besu.crypto.crosschain.threshold;

import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.crosschain.threshold.protocol.CrosschainCoordinationContract;
import org.hyperledger.besu.crypto.crosschain.threshold.protocol.Node;
import org.hyperledger.besu.crypto.crosschain.threshold.protocol.ThresholdKeyGenContract;
import org.hyperledger.besu.crypto.crosschain.threshold.scheme.IntegerSecretShare;
import org.hyperledger.besu.crypto.crosschain.threshold.scheme.ThresholdScheme;

import java.math.BigInteger;
import java.util.Date;

// This is the main class for running through a simple scenario.
public class CrosschainCheck {
  static final int THRESHOLD = 3;
  static final int TOTAL_NUMBER_NODES = 5;

  static final int COORDINATING_NODE = 0;

  Node[] nodes;
  CrosschainCoordinationContract ccc;
  ThresholdKeyGenContract th;

  static byte[] DATA = new byte[] {0x01, 0x02, 0x03, 0x04};

  // Initialise the network of nodes.
  void initNodes() throws Exception {
    this.ccc = new CrosschainCoordinationContract();
    this.th = new ThresholdKeyGenContract(THRESHOLD, TOTAL_NUMBER_NODES);

    this.nodes = new Node[TOTAL_NUMBER_NODES];
    for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
      this.nodes[i] = new Node(i, THRESHOLD, TOTAL_NUMBER_NODES);
    }

    for (Node node : this.nodes) {
      node.initNode(this.nodes, this.ccc, this.th);
    }
  }

  void thresholdKeyGeneration() throws Exception {
    // Any node can lead the key generation process.
    // TODO: How to determine which node should trigger a key generation sequence /  how to do this.
    this.nodes[COORDINATING_NODE].doKeyGeneration();
  }

  void checkPublicKey() throws Exception {
    // Calculate the group private key.
    // In a real situation, this private key is never combined.

    // Add all of the points for each of the x values.
    BigInteger[] xValues = this.th.getAllNodeIds();

    for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
      IntegerSecretShare share =
          new IntegerSecretShare(xValues[i], this.nodes[i].getPrivateKeyShare());
      System.out.println("Share: " + share);
    }
    IntegerSecretShare[] shares = new IntegerSecretShare[THRESHOLD];
    for (int i = 0; i < THRESHOLD; i++) {
      shares[i] = new IntegerSecretShare(xValues[i], this.nodes[i].getPrivateKeyShare());
    }

    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    ThresholdScheme thresholdScheme = new ThresholdScheme(cryptoProvider, THRESHOLD);

    // Do Lagrange interpolation to determine the group private key (the point for x=0).
    BigInteger privateKey = thresholdScheme.calculateSecret(shares);
    System.out.println("Private Key: " + privateKey);

    BlsPoint shouldBePublicKey = cryptoProvider.createPointE2(privateKey);
    System.out.println("Public Key derived from private key: " + shouldBePublicKey);

    BlsPoint pubKey = this.nodes[0].getPublicKey();
    System.out.println("Public Key derived from public shares: " + pubKey);

    if (shouldBePublicKey.equals(pubKey)) {
      System.out.println("Key generation worked!!!!!");
    } else {
      throw new Exception("Private key and public key did not match!");
    }
  }

  BlsPoint thresholdSign(final byte[] toBeSigned) throws Exception {
    return this.nodes[COORDINATING_NODE].sign(toBeSigned);
  }

  boolean thresholdVerify(final byte[] toBeVerified, final BlsPoint signature) {
    return this.nodes[COORDINATING_NODE].verify(toBeVerified, signature);
  }

  public static void main(final String[] args) throws Exception {

    // Make stdout and stderr one stream. Have them both non-buffered.
    // What this means is that if an error or exception stack trace is thrown,
    // it will be shown in the context of the other output.
    System.setOut(System.err);

    System.out.println("Test: Start");
    System.out.println(" Date: " + (new Date().toString()));
    System.out.println();

    if (THRESHOLD > TOTAL_NUMBER_NODES) {
      throw new Exception("Configuration Error: THRESHOLD > TOTAL_NUMBER_NODES !");
    }

    CrosschainCheck test = new CrosschainCheck();
    test.initNodes();
    test.thresholdKeyGeneration();

    test.checkPublicKey();

    BlsPoint signature = test.thresholdSign(DATA);
    boolean verified = test.thresholdVerify(DATA, signature);

    System.out.println("Signature verified: " + verified);

    System.out.println();
    System.out.println(" Date: " + (new Date().toString()));
    System.out.println("Test: End");
  }
}
