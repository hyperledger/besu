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
package org.hyperledger.besu.crosschain.p2p;

import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGenContractInterface;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.ThresholdScheme;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.PRNGSecureRandom;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO REMOVE ONCE DEV P2P Done
public class SimulatedOtherNode {

  protected static final Logger LOG = LogManager.getLogger();

  private int threshold;
  private SecureRandom prng = new PRNGSecureRandom();

  private Map<BigInteger, BigInteger> mySecretShares;
  private BlsPoint[] myCoeffsPublicValues;
  private Bytes32[] myCoeffsPublicValueCommitments;
  private BigInteger myNodeAddress;
  private List<BigInteger> nodesStillActiveInKeyGeneration;

  private Map<BigInteger, BigInteger> receivedSecretShares = new TreeMap<>();

  ThresholdKeyGenContractInterface thresholdKeyGenContract;
  CrosschainDevP2PInterface p2p;

  private Map<BigInteger, BlsPoint[]> otherNodeCoefficients;

  private BlsCryptoProvider cryptoProvider;

  private BigInteger privateKeyShare = null;

  private BlsPoint publicKey = null;

  private ThresholdScheme thresholdScheme;

  public SimulatedOtherNode(
      final BigInteger nodeAddress,
      final ThresholdKeyGenContractInterface thresholdKeyGenContract,
      final CrosschainDevP2PInterface p2p) {
    this.thresholdKeyGenContract = thresholdKeyGenContract;
    this.p2p = p2p;
    this.cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    this.myNodeAddress = nodeAddress;
  }

  public void init() {
    this.p2p.addSimulatedOtherNode(this.myNodeAddress, this);
  }

  public void requestStartNewKeyGeneration(final long keyVersionNumber) {
    LOG.info("start {}", myNodeAddress);
    this.threshold = this.thresholdKeyGenContract.getThreshold(keyVersionNumber);
    this.thresholdScheme = new ThresholdScheme(this.cryptoProvider, this.threshold, this.prng);
    thresholdKeyGenContract.setNodeId(keyVersionNumber, myNodeAddress);
  }

  public void requestPostCommits(final long keyVersionNumber) {
    // TODO: After some time-out, to allow other nodes to post their node ids.
    int numberOfNodes = thresholdKeyGenContract.getNumberOfNodes(keyVersionNumber);
    if (numberOfNodes < threshold) {
      throw new Error(
          "Key generation has failed. Not enough nodes participated by posting X values.");
    }

    // Post Commitments Round
    nodesStillActiveInKeyGeneration = new ArrayList<>();
    BigInteger[] nodeAddresses = new BigInteger[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      BigInteger address = thresholdKeyGenContract.getNodeAddress(keyVersionNumber, i);
      nodesStillActiveInKeyGeneration.add(address);
      nodeAddresses[i] = address;
    }

    generatePartsOfKeySharesPublicValueAndCommitments(nodeAddresses);
    thresholdKeyGenContract.setNodeCoefficientsCommitments(
        keyVersionNumber, this.myNodeAddress, myCoeffsPublicValueCommitments);
  }

  public void requestPostPublicValues(final long keyVersionNumber) {
    // Post Public Values Round.
    LOG.info("Post Public Values");
    // TODO only publish the public values after all of the commitments are posted.
    // Post public values of coefficient to threshold key gen contract.
    thresholdKeyGenContract.setNodeCoefficientsPublicValues(
        keyVersionNumber, this.myNodeAddress, myCoeffsPublicValues);
  }

  public void requestGetOtherNodeCoefs(final long keyVersionNumber) {
    // Get all of the other node's coefficient public values.
    LOG.info("Get all of the other node's coefficient public values.");
    otherNodeCoefficients = new TreeMap<BigInteger, BlsPoint[]>();
    for (BigInteger nodeAddress : nodesStillActiveInKeyGeneration) {
      if (!nodeAddress.equals(myNodeAddress)) {
        BlsPoint[] points = new BlsPoint[myCoeffsPublicValues.length];
        for (int j = 0; j < myCoeffsPublicValues.length; j++) {
          points[j] =
              thresholdKeyGenContract.getCoefficientPublicValue(keyVersionNumber, nodeAddress, j);
        }
        otherNodeCoefficients.put(nodeAddress, points);
      }
    }
  }

  public void requestSendPrivateValues(final long keyVersionNumber) {
    // TODO send private values
    // TODO Note that the nodeAddresses will have had some purged for nodes that have not
    // posted the commitments or public values.
    p2p.simulatedNodesSendPrivateValues(
        myNodeAddress, nodesStillActiveInKeyGeneration, mySecretShares);
  }

  public void requestNodesCompleteKeyGen() {
    // Calculate private key shares and public key round.
    // TODO need to account for some private key shares not being sent / nefarious actors.
    privateKeyShare = calculateMyPrivateKeyShare();

    publicKey = calculatePublicKey();
    LOG.info("done {}", myNodeAddress);
  }

  private void generatePartsOfKeySharesPublicValueAndCommitments(final BigInteger[] xValues) {
    // Generate random coefficients.
    BigInteger[] coeffs = thresholdScheme.generateRandomCoefficients();

    // Generate the secret share parts (the y values).
    BigInteger[] myPartSecretShares = thresholdScheme.generateShares(xValues, coeffs);
    this.mySecretShares = new TreeMap<>();
    for (int i = 0; i < xValues.length; i++) {
      this.mySecretShares.put(xValues[i], myPartSecretShares[i]);
    }

    // Generate public values.
    this.myCoeffsPublicValues = new BlsPoint[coeffs.length];
    for (int i = 0; i < coeffs.length; i++) {
      this.myCoeffsPublicValues[i] = this.cryptoProvider.createPointE2(coeffs[i]);
    }

    // Create and post the commitments to the coefficient public values.
    this.myCoeffsPublicValueCommitments = new Bytes32[coeffs.length];
    for (int i = 0; i < coeffs.length; i++) {
      byte[] coefPubBytes = myCoeffsPublicValues[i].store();
      this.myCoeffsPublicValueCommitments[i] = Hash.keccak256(BytesValue.wrap(coefPubBytes));
    }
  }

  private BigInteger calculateMyPrivateKeyShare() {
    BigInteger privateKeyShareAcc = this.mySecretShares.get(this.myNodeAddress);
    privateKeyShareAcc = this.cryptoProvider.modPrime(privateKeyShareAcc);

    for (BigInteger nodeAddress : this.nodesStillActiveInKeyGeneration) {
      if (!nodeAddress.equals(this.myNodeAddress)) {
        privateKeyShareAcc = privateKeyShareAcc.add(this.receivedSecretShares.get(nodeAddress));
        privateKeyShareAcc = this.cryptoProvider.modPrime(privateKeyShareAcc);
      }
    }
    return privateKeyShareAcc;
  }

  public BlsPoint getPublicKey() {
    return this.publicKey;
  }

  public BigInteger getPrivateKeyShare() {
    return this.privateKeyShare;
  }

  public BigInteger getMyNodeAddress() {
    return this.myNodeAddress;
  }

  /**
   * The public key is the sum of the constant coefficient for all curves.
   *
   * <p>That is, the public key is the point for X=0. Given equations y = a x^3 + b x^2 + c x + d,
   * the x = 0 value is d. Summing the d values for all curves gives the public key.
   */
  private BlsPoint calculatePublicKey() {
    final int numCoeffs = this.threshold - 1;
    BlsPoint yValue = this.myCoeffsPublicValues[numCoeffs];

    for (BigInteger nodeAddress : this.nodesStillActiveInKeyGeneration) {
      if (!nodeAddress.equals(this.myNodeAddress)) {
        BlsPoint pubShare = this.otherNodeCoefficients.get(nodeAddress)[numCoeffs];
        yValue = yValue.add(pubShare);
      }
    }

    return yValue;
  }

  public void receivePrivateValue(final BigInteger fromNodeAddress, final BigInteger share) {
    // Check that the secret share corresponds to a public value which is on the curve
    // defined by the coefficients the node published to the ThresholdKeyGenContract.
    BlsPoint publicKeyShare = cryptoProvider.createPointE2(share);

    BlsPoint[] coefs = otherNodeCoefficients.get(fromNodeAddress);
    if (coefs == null) {
      LOG.error("Private key share {} sent by unknown node {}", share, fromNodeAddress);
      throw new Error("Private key share sent by unknown node not handled yet");
    }

    BlsPoint calculatedPublicKeyShare =
        thresholdScheme.generatePublicKeyShare(myNodeAddress, coefs);

    if (!publicKeyShare.equals(calculatedPublicKeyShare)) {
      LOG.error("Private share from {} did not match public coefficients.", fromNodeAddress);
      throw new Error("Private share not matching public coefs not handled yet");
    }

    receivedSecretShares.put(fromNodeAddress, share);
  }
}
