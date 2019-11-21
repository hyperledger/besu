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
package org.hyperledger.besu.crosschain.core.keys;

import org.hyperledger.besu.crosschain.core.keys.generation.SimulatedThresholdKeyGenContractWrapper;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGenContractInterface;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGeneration;
import org.hyperledger.besu.crosschain.p2p.CrosschainDevP2PInterface;
import org.hyperledger.besu.crosschain.p2p.SimulatedCrosschainDevP2P;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainKeyManager {
  protected static final Logger LOG = LogManager.getLogger();

  public enum ActiveCredentialStatus {
    // No credentials available for this node. The Crosschain Coordination Contract
    // does not have any public key specified for this blockchain.
    NO_CREDENTIALS,

    // The credentials related to the active public key specified by the Crosschain
    // Coordination Contract are not available on this node.
    ACTIVE_CREDENTIALS_NOT_AVAILABLE_ON_THIS_NODE,
    ACTIVE_CREDENTIALS_AVAILABLE
  }

  public enum NegotiatingCredentialsStatus {
    NO_NEGOTIATION,
    ACTIVE_NEGOTIATION,
    NEGOTIATED_CREDENTIALS_READY
  }

  private static long NOT_KNOWN = -1;
  // long highestKnownVersion = NOT_KNOWN;
  long activeKeyVersion = NOT_KNOWN;
  boolean activeVersionNotKnown = true;

  // TODO blockkchain ID will be used when interacting with the crosschain coordination contract.
  //  private BigInteger blockchainId;
  private SECP256K1.KeyPair nodeKeys;

  private static class Coord {
    BigInteger coordinationBlockchainId;
    Address coodinationContract;

    Coord(final BigInteger coordinationBlockchainId, final Address coodinationContract) {
      this.coodinationContract = coodinationContract;
      this.coordinationBlockchainId = coordinationBlockchainId;
    }
  }

  List<Coord> coordinationContracts = new ArrayList<>();

  Map<Long, BlsThresholdCredentials> credentials;

  public Map<Long, ThresholdKeyGeneration> activeKeyGenerations = new TreeMap<>();
  ThresholdKeyGenContractInterface thresholdKeyGenContract;
  CrosschainDevP2PInterface p2p;

  // TODO add key generation contract address
  public static CrosschainKeyManager getCrosschainKeyManager() {
    // TODO when real versions of p2p and key gen contract exist, this is the place to link them in.
    ThresholdKeyGenContractInterface keyGen = new SimulatedThresholdKeyGenContractWrapper();
    CrosschainDevP2PInterface p2pI = new SimulatedCrosschainDevP2P(keyGen);
    return new CrosschainKeyManager(keyGen, p2pI);
  }

  public CrosschainKeyManager(
      final ThresholdKeyGenContractInterface thresholdKeyGenContract,
      final CrosschainDevP2PInterface p2p) {
    this.thresholdKeyGenContract = thresholdKeyGenContract;
    this.p2p = p2p;

    this.credentials = CrosschainKeyManagerStorage.loadAllCredentials();
    if (this.credentials.size() != 0) {
      // TODO Set the highest known key number.

      // TODO check with the Coordination Contract to see what the active version is.

      // TODO check that this node has credentials for the version that is the "active version".
    }
  }

  public void init(final BigInteger sidechainId, final SECP256K1.KeyPair nodeKeys) {
    //    this.blockchainId = sidechainId;
    this.nodeKeys = nodeKeys;

    this.thresholdKeyGenContract.init(nodeKeys);
  }

  public void addCoordinationContract(
      final BigInteger coordinationBlockchainId, final Address coodinationContract) {
    // TODO check that this coodination contrat is not already in the list.
    this.coordinationContracts.add(new Coord(coordinationBlockchainId, coodinationContract));
  }

  public void removeCoordinationContract(
      final BigInteger coordinationBlockchainId, final Address coodinationContract) {
    // TODO
  }

  public BlsThresholdCredentials getActiveCredentials() {
    if (this.activeVersionNotKnown) {
      return BlsThresholdCredentials.emptyCredentials();
    }
    return this.credentials.get(activeKeyVersion);
  }

  public BlsThresholdCredentials getGenerationCompleteCredentials() {
    return null;
  }

  /**
   * Coordinate with other nodes to generate a new threshold key set.
   *
   * @param threshold The threshold number of keys that need to cooperate to sign messages.
   * @return The key version number of the key.
   */
  public long generateNewKeys(final int threshold) {
    ThresholdKeyGeneration keyGen =
        new ThresholdKeyGeneration(
            threshold, this.nodeKeys, this.thresholdKeyGenContract, this.p2p);
    long keyVersionNumber = keyGen.startKeyGeneration();
    this.activeKeyGenerations.put(keyVersionNumber, keyGen);
    return keyVersionNumber;
  }

  /**
   * Coordinate with other nodes to sign the message.
   *
   * @param message The message to be signed.
   * @return The signed message.
   */
  //  private BytesValue thresholdSign(final BytesValue message) {
  //    // TODO this is going to need to be re-written assuming asynchronous signature results
  //
  //  }
  //
  //  public BytesValue signSubordinateViewResult(final BytesValue message) {
  //    LOG.info("Subordinate View Result: coordinating the signing of message: {}", message);
  //    return thresholdSign(message);
  //  }
  //
  //
  //  public BytesValue localSign(final long keyVersion, final BytesValue message) {
  //
  //  }

}
