/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgentFactory;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatService;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.MutableDiscoverySystem;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Factory for creating DiscV5 {@link PeerDiscoveryAgent} instances backed by the Ethereum Discovery
 * v5 {@link DiscoverySystemBuilder}.
 *
 * <p>This factory is responsible for:
 *
 * <ul>
 *   <li>Initializing the local {@link NodeRecord} via {@link NodeRecordManager}
 *   <li>Configuring and building a mutable DiscV5 discovery system
 *   <li>Wiring Besu-specific components such as fork ID handling and node key services
 * </ul>
 *
 * <p>The resulting {@link PeerDiscoveryAgent} integrates DiscV5 discovery with Besu’s P2P
 * networking stack.
 */
public final class PeerDiscoveryAgentFactoryV5 implements PeerDiscoveryAgentFactory {
  private final NetworkingConfiguration config;

  private final NodeRecordManager nodeRecordManager;
  private final NodeKey nodeKey;
  private final ForkIdManager forkIdManager;

  /**
   * Creates a new DiscV5 peer discovery agent factory.
   *
   * @param nodeKey the local node key used for identity and signing
   * @param config the networking configuration
   * @param natService NAT service for external address discovery
   * @param storageProvider storage provider for persisting node records
   * @param forkIdManager manager providing fork ID information for peer compatibility
   */
  public PeerDiscoveryAgentFactoryV5(
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final NatService natService,
      final StorageProvider storageProvider,
      final ForkIdManager forkIdManager) {
    this.config = config;
    this.nodeKey = nodeKey;
    this.forkIdManager = forkIdManager;
    this.nodeRecordManager =
        new NodeRecordManager(storageProvider, nodeKey, forkIdManager, natService);
  }

  /**
   * Creates and configures a DiscV5 {@link PeerDiscoveryAgent}.
   *
   * @param rlpxAgent the RLPx agent
   * @return a fully configured DiscV5 peer discovery agent
   * @throws IllegalStateException if the local node record has not been initialized
   */
  @Override
  public PeerDiscoveryAgent create(final RlpxAgent rlpxAgent) {
    final NodeRecord localNodeRecord = initializeLocalNodeRecord();

    final MutableDiscoverySystem discoverySystem =
        new DiscoverySystemBuilder()
            .listen(config.getDiscovery().getBindHost(), config.getDiscovery().getBindPort())
            .signer(new LocalNodeKeySigner(nodeKey))
            .localNodeRecord(localNodeRecord)
            .localNodeRecordListener((previous, updated) -> nodeRecordManager.updateNodeRecord())
            .newAddressHandler((nodeRecord, newAddress) -> Optional.of(nodeRecord))
            // TODO Integrate address filtering based on peer permissions
            .addressAccessPolicy(AddressAccessPolicy.ALLOW_ALL)
            .buildMutable();

    return new PeerDiscoveryAgentV5(
        discoverySystem, config, forkIdManager, nodeRecordManager, rlpxAgent);
  }

  /**
   * Initializes the local node record using the {@link NodeRecordManager}.
   *
   * @return the initialized local {@link NodeRecord}
   * @throws IllegalStateException if the local node record has not been initialized
   */
  private NodeRecord initializeLocalNodeRecord() {
    nodeRecordManager.initializeLocalNode(
        config.getDiscovery().getAdvertisedHost(),
        config.getDiscovery().getBindPort(),
        config.getDiscovery().getBindPort());

    return nodeRecordManager
        .getLocalNode()
        .flatMap(DiscoveryPeer::getNodeRecord)
        .orElseThrow(() -> new IllegalStateException("Local node record not initialized"));
  }

  /**
   * An implementation of the {@link org.ethereum.beacon.discovery.crypto.Signer} interface that
   * uses a local {@link NodeKey} for signing and key agreement.
   */
  private static class LocalNodeKeySigner implements org.ethereum.beacon.discovery.crypto.Signer {
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

    private final NodeKey nodeKey;

    /**
     * Creates a new LocalNodeKeySigner.
     *
     * @param nodeKey the node key to use for signing and key agreement
     */
    public LocalNodeKeySigner(final NodeKey nodeKey) {
      this.nodeKey = nodeKey;
    }

    /**
     * Derives a shared secret using ECDH with the given peer public key.
     *
     * @param remotePubKey the destination peer's public key
     * @return the derived shared secret
     */
    @Override
    public Bytes deriveECDHKeyAgreement(final Bytes remotePubKey) {
      SECPPublicKey publicKey = signatureAlgorithm.createPublicKey(remotePubKey);
      return nodeKey.calculateECDHKeyAgreement(publicKey);
    }

    /**
     * Creates a signature of message `x`.
     *
     * @param messageHash message, hashed
     * @return ECDSA signature with properties merged together: r || s
     */
    @Override
    public Bytes sign(final Bytes32 messageHash) {
      Bytes signature = nodeKey.sign(messageHash).encodedBytes();
      return signature.slice(0, 64);
    }

    /**
     * Derives the compressed public key corresponding to the private key held by this module.
     *
     * @return the compressed public key
     */
    @Override
    public Bytes deriveCompressedPublicKeyFromPrivate() {
      return Bytes.wrap(
          signatureAlgorithm.publicKeyAsEcPoint(nodeKey.getPublicKey()).getEncoded(true));
    }
  }
}
