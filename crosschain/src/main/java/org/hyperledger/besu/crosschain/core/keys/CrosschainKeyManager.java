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

import org.hyperledger.besu.crosschain.core.keys.generation.KeyGenFailureToCompleteReason;
import org.hyperledger.besu.crosschain.core.keys.generation.SimulatedThresholdKeyGenContractWrapper;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGenContractInterface;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGeneration;
import org.hyperledger.besu.crosschain.p2p.CrosschainDevP2PInterface;
import org.hyperledger.besu.crosschain.p2p.SimulatedCrosschainDevP2P;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainKeyManager {
  protected static final Logger LOG = LogManager.getLogger();

  public enum CredentialStatus {
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

  // TODO blockkchain ID will be used when interacting with the crosschain coordination contract.
  //  private BigInteger blockchainId;
  private SECP256K1.KeyPair nodeKeys;

  Map<String, CoordinationContractInformation> coordinationContracts = new TreeMap<>();

  private long NO_ACTIVE_VERSION = -1;
  long activeKeyVersion = NO_ACTIVE_VERSION;

  public Map<Long, ThresholdKeyGeneration> activeKeyGenerations = new TreeMap<>();

  Map<Long, BlsThresholdCredentials> credentials;

  ThresholdKeyGenContractInterface thresholdKeyGenContract;
  CrosschainDevP2PInterface p2p;

  BigInteger blockchainId;

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
    this.blockchainId = sidechainId;
    this.nodeKeys = nodeKeys;

    this.thresholdKeyGenContract.init(nodeKeys);
  }

  public void addCoordinationContract(
      final BigInteger coordinationBlockchainId,
      final Address coodinationContractAddress,
      final String ipAddressAndPort) {
    String key = coordinationBlockchainId.toString(16) + coodinationContractAddress.getHexString();
    this.coordinationContracts.put(
        key,
        new CoordinationContractInformation(
            coordinationBlockchainId, coodinationContractAddress, ipAddressAndPort));
  }

  public void removeCoordinationContract(
      final BigInteger coordinationBlockchainId, final Address coodinationContractAddress) {
    String key = coordinationBlockchainId.toString(16) + coodinationContractAddress.getHexString();
    this.coordinationContracts.remove(key);
  }

  public Collection<CoordinationContractInformation> getAllCoordinationContracts() {
    return this.coordinationContracts.values();
  }

  public void setKeyGenerationContractAddress(final Address address) {
    // TODO
    throw new Error("Not implemented yet");
  }

  /**
   * Coordinate with other nodes to generate a new threshold key set.
   *
   * @param threshold The threshold number of keys that need to cooperate to sign messages.
   * @param algorithm The ECC curve and message digest function to be used.
   * @return The key version number of the key.
   */
  public long generateNewKeys(final int threshold, final BlsThresholdCryptoSystem algorithm) {
    ThresholdKeyGeneration keyGen =
        new ThresholdKeyGeneration(
            threshold,
            this.blockchainId,
            algorithm,
            this.nodeKeys,
            this.thresholdKeyGenContract,
            this.p2p);
    long keyVersionNumber = keyGen.startKeyGeneration();
    this.activeKeyGenerations.put(keyVersionNumber, keyGen);
    return keyVersionNumber;
  }

  public KeyStatus getKeyStatus(final long keyVersion) {
    BlsThresholdCredentials credentials = this.credentials.get(keyVersion);
    if (credentials != null) {
      return credentials.getKeyStatus();
    }
    ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
    if (keyGeneration != null) {
      return keyGeneration.getKeyStatus();
    }
    return KeyStatus.UNKNOWN_KEY;
  }

  public Map<BigInteger, KeyGenFailureToCompleteReason> getKeyGenNodesDroppedOutOfKeyGeneration(
      final long keyVersion) {
    BlsThresholdCredentials credentials = this.credentials.get(keyVersion);
    if (credentials != null) {
      return credentials.getNodesDoppedOutOfKeyGeneration();
    }
    ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
    if (keyGeneration != null) {
      return keyGeneration.getNodesNoLongerInKeyGeneration();
    }
    return new TreeMap<>();
  }

  public KeyGenFailureToCompleteReason getKeyGenFailureReason(final long keyVersion) {
    BlsThresholdCredentials credentials = this.credentials.get(keyVersion);
    if (credentials != null) {
      return KeyGenFailureToCompleteReason.SUCCESS;
    }
    ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
    if (keyGeneration != null) {
      return keyGeneration.getFailureReason();
    }
    return KeyGenFailureToCompleteReason.UNKNOWN_KEY;
  }

  public Set<BigInteger> getKeyGenActiveNodes(final long keyVersion) {
    BlsThresholdCredentials credentials = this.credentials.get(keyVersion);
    if (credentials != null) {
      return credentials.getNodesCompletedKeyGeneration();
    }
    ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
    if (keyGeneration != null) {
      return keyGeneration.getNodesStillActiveInKeyGeneration();
    }
    return new TreeSet<>();
  }

  public void activateKey(final long keyVersion) {
    if (keyVersion == this.activeKeyVersion) {
      // The key version is already active: there is nothing to do.
      return;
    }

    // TODO Check crosshcain coordination contract to make sure this key version is the active
    // version

    // TODO send a signalling transaction to all nodes requesting they check the crosschain
    // coordination contract.

    // Check to see if the key version represents an existing key. That is, the decision may have
    // been made to switch to an older key.
    BlsThresholdCredentials oldCredentials = this.credentials.get(keyVersion);
    if (oldCredentials == null) {
      // Check to see if there is a key generation that matches the key version.
      ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
      if ((keyGeneration != null)
          && (keyGeneration.getKeyStatus().equals(KeyStatus.KEY_GEN_COMPLETE))) {
        this.credentials.put(keyVersion, keyGeneration.getCredentials());
        this.activeKeyGenerations.remove(keyVersion);
      }
      // If the key isn't ready or doesn't exist, then just ignore the request.
      return;
    }
    this.activeKeyVersion = keyVersion;
  }

  public long getActiveKeyVersion() {
    return this.activeKeyVersion;
  }

  public BlsThresholdPublicKey getPublicKey(final long keyVersion) {
    BlsThresholdCredentials credentials = this.credentials.get(keyVersion);
    if (credentials != null) {
      return credentials;
    }
    ThresholdKeyGeneration keyGeneration = this.activeKeyGenerations.get(keyVersion);
    if (keyGeneration != null) {
      return keyGeneration.getCredentials();
    }
    return BlsThresholdPublicKey.NONE;
  }

  public BlsThresholdPublicKey getActivePublicKey() {
    return getPublicKey(this.activeKeyVersion);
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
