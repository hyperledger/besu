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
package org.hyperledger.besu.crosschain.core.keys.generation;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.ThresholdScheme;
import org.hyperledger.besu.crosschain.p2p.CrosschainDevP2PInterface;
import org.hyperledger.besu.crosschain.p2p.CrosschainPartSecretShareCallback;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.PRNGSecureRandom;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
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

public class ThresholdKeyGeneration {
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

  public ThresholdKeyGeneration(
      final int threshold,
      final SECP256K1.KeyPair nodeKeyPair,
      final ThresholdKeyGenContractInterface thresholdKeyGenContract,
      final CrosschainDevP2PInterface p2p) {
    this.threshold = threshold;
    this.thresholdKeyGenContract = thresholdKeyGenContract;
    this.p2p = p2p;
    this.cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);

    this.thresholdScheme = new ThresholdScheme(this.cryptoProvider, this.threshold, this.prng);

    // TODO want the address based on the public key.
    // Create a node id based on the public key.
    SECP256K1.PublicKey publicKey = nodeKeyPair.getPublicKey();
    this.myNodeAddress = new BigInteger(Address.extract(publicKey).toUnprefixedString(), 16);
  }

  public long startKeyGeneration() {
    try {
      long keyVersionNumber = this.thresholdKeyGenContract.getExpectedKeyGenerationVersion();

      this.p2p.setSecretShareCallback(new CrosschainPartSecretShareCallbackImpl());
      this.p2p.setMyNodeAddress(this.myNodeAddress);

      // TODO: Put the following in a "do later" clause
      this.thresholdKeyGenContract.startNewKeyGeneration(keyVersionNumber, this.threshold);

      // TODO: Put the following in a "do later" clause
      // Request all nodes start the process in parallel with this node.
      this.p2p.requestStartNewKeyGeneration(keyVersionNumber);

      // TODO: After some time-out, to allow other nodes to post their node ids.
      // TODO Use vertix
      // Probably have to wait multiple block times.
      // Thread.sleep(2000);
      this.p2p.requestPostCommits(keyVersionNumber);

      int numberOfNodes = this.thresholdKeyGenContract.getNumberOfNodes(keyVersionNumber);
      if (numberOfNodes < this.threshold) {
        // Key generation has failed. Not enough nodes participated by posting X values.
        // TODO indicate failure some how
        throw new RuntimeException("Number of nodes less than threshold");
      }

      // Post Commitments Round
      this.nodesStillActiveInKeyGeneration = new ArrayList<>();
      BigInteger[] nodeAddresses = new BigInteger[numberOfNodes];
      for (int i = 0; i < numberOfNodes; i++) {
        BigInteger address = this.thresholdKeyGenContract.getNodeAddress(keyVersionNumber, i);
        this.nodesStillActiveInKeyGeneration.add(address);
        nodeAddresses[i] = address;
      }

      generatePartsOfKeySharesPublicValueAndCommitments(nodeAddresses);
      this.thresholdKeyGenContract.setNodeCoefficientsCommitments(
          keyVersionNumber, this.myCoeffsPublicValueCommitments);

      // TODO wait for a period of time, to let other nodes post their commitments.
      // Probably have to wait multiple block times.
      // Thread.sleep(2000);

      // Post Public Values Round.
      this.p2p.requestPostPublicValues(keyVersionNumber);
      LOG.info("Post Public Values");
      // TODO only publish the public values after all of the commitments are posted.
      // Post public values of coefficient to threshold key gen contract.
      this.thresholdKeyGenContract.setNodeCoefficientsPublicValues(
          keyVersionNumber, this.myCoeffsPublicValues);

      // Probably have to wait multiple block times.
      // Thread.sleep(2000);
      // Get all of the other node's coefficient public values.
      LOG.info("Get all of the other node's coefficient public values.");
      this.otherNodeCoefficients = new TreeMap<BigInteger, BlsPoint[]>();
      LOG.info("****** other node created!!!");
      LOG.info("my address: {}", this.myNodeAddress);
      for (BigInteger nodeAddress : this.nodesStillActiveInKeyGeneration) {
        LOG.info("getting coeff public values for node: {}", nodeAddress);
        if (!nodeAddress.equals(this.myNodeAddress)) {
          BlsPoint[] points = new BlsPoint[this.myCoeffsPublicValues.length];
          for (int j = 0; j < this.myCoeffsPublicValues.length; j++) {
            LOG.info(
                "this.thresholdKeyGenContract.getCoefficientPublicValue for node: {}", nodeAddress);
            points[j] =
                this.thresholdKeyGenContract.getCoefficientPublicValue(
                    keyVersionNumber, nodeAddress, j);
          }
          this.otherNodeCoefficients.put(nodeAddress, points);
          LOG.info("other coeffs added for node: {}", nodeAddress);
        }
      }
      this.p2p.requestGetOtherNodeCoefs(keyVersionNumber);

      // TODO send private values
      // TODO Note that the nodeAddresses will have had some purged for nodes that have not posted
      // the commitments or public values.
      this.p2p.sendPrivateValues(
          this.myNodeAddress, this.nodesStillActiveInKeyGeneration, this.mySecretShares);
      this.p2p.requestSendPrivateValues(keyVersionNumber);

      this.p2p.requestNodesCompleteKeyGen();
      // Calculate private key shares and public key round.
      // TODO need to account for some private key shares not being sent / nefarious actors.
      this.privateKeyShare = calculateMyPrivateKeyShare();

      this.publicKey = calculatePublicKey();

      return keyVersionNumber;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
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

  /**
   * The public key is the sum of the constant coefficient for all curves.
   *
   * <p>That is, the public key is the point for X=0. Given equations y = a x^3 + b x^2 + c x + d,
   * the x = 0 value is d. Summing the d values for all curves gives the public key.
   */
  private BlsPoint calculatePublicKey() {
    LOG.info("calculatePublicKey othernodecoeffs: {}", otherNodeCoefficients);

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

  class CrosschainPartSecretShareCallbackImpl implements CrosschainPartSecretShareCallback {
    @Override
    public void storePrivateSecretShareCallback(
        final BigInteger nodeId, final BigInteger secretShare) {
      synchronized (this) {
        // Check that the secret share corresponds to a public value which is on the curve
        // defined by the coefficients the node published to the ThresholdKeyGenContract.
        BlsPoint publicKeyShare = cryptoProvider.createPointE2(secretShare);

        LOG.info("thresholdscheme: {}", thresholdScheme);
        LOG.info("othernodecoeffs: {}", otherNodeCoefficients);
        LOG.info("othernodecoeffs.len: {}", otherNodeCoefficients.size());

        BlsPoint[] otherNodeCoefs = otherNodeCoefficients.get(nodeId);
        if (otherNodeCoefs == null) {
          throw new Error("Unexpectedly, no coefficients for node: " + nodeId);
        }
        BlsPoint calculatedPublicKeyShare =
            thresholdScheme.generatePublicKeyShare(
                myNodeAddress, otherNodeCoefficients.get(nodeId));

        if (!publicKeyShare.equals(calculatedPublicKeyShare)) {
          LOG.error("Private share from {} did not match public coefficients.", nodeId);
          // TODO we need to indicate this key generation failure.
        }

        receivedSecretShares.put(nodeId, secretShare);
      }
    }
  }

  // JUST FOR TESTING
  public BigInteger getPrivateKeyShare() {
    return this.privateKeyShare;
  }

  public BigInteger getMyNodeAddress() {
    return this.myNodeAddress;
  }
}
