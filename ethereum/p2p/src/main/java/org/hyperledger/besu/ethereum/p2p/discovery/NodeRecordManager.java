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
package org.hyperledger.besu.ethereum.p2p.discovery;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.chain.VariablesStorage;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.discovery.discv4.internal.DiscoveryPeerV4;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.net.InetAddresses;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the local Ethereum Node Record (ENR) lifecycle.
 *
 * <p>This component is responsible for:
 *
 * <ul>
 *   <li>Initializing the local {@link DiscoveryPeerV4} representation
 *   <li>Creating, updating, and signing the local {@link NodeRecord}
 *   <li>Persisting the ENR sequence number and contents to disk
 *   <li>Ensuring the ENR remains consistent with the advertised address, ports, and fork ID
 * </ul>
 *
 * <p>The ENR is only rewritten when one or more relevant fields change.
 */
public class NodeRecordManager {
  private static final Logger LOG = LoggerFactory.getLogger(NodeRecordManager.class);
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private static final String FORK_ID_ENR_FIELD = "eth";

  private final VariablesStorage variablesStorage;
  private final NodeKey nodeKey;
  private final Bytes nodeId;
  private final Supplier<List<Bytes>> forkIdSupplier;
  private final NatService natService;

  private Optional<DiscoveryPeerV4> localNode = Optional.empty();
  private String advertisedAddress;

  /**
   * Creates a new {@link NodeRecordManager}.
   *
   * <p>The manager derives the node identifier from the provided {@link NodeKey} and lazily
   * resolves the fork ID using the supplied {@link ForkIdManager}.
   *
   * @param storageProvider provides access to persistent ENR storage
   * @param nodeKey the local node's cryptographic identity
   * @param forkIdManager supplies the current fork ID for the chain head
   * @param natService resolves externally advertised network addresses
   */
  public NodeRecordManager(
      final StorageProvider storageProvider,
      final NodeKey nodeKey,
      final ForkIdManager forkIdManager,
      final NatService natService) {

    this.variablesStorage = storageProvider.createVariablesStorage();
    this.nodeKey = nodeKey;
    this.nodeId = nodeKey.getPublicKey().getEncodedBytes();
    this.forkIdSupplier = () -> forkIdManager.getForkIdForChainHead().getForkIdAsBytesList();
    this.natService = natService;
  }

  /**
   * Returns the locally initialized discovery peer, if present.
   *
   * <p>The local node is only available after {@link #initializeLocalNode(String, int, int)} has
   * been invoked.
   *
   * @return an {@link Optional} containing the local {@link DiscoveryPeerV4}, or empty if
   *     uninitialized
   */
  public Optional<DiscoveryPeerV4> getLocalNode() {
    return localNode;
  }

  /**
   * Initializes the local discovery peer and creates or updates the corresponding ENR.
   *
   * <p>The advertised host may be overridden if the {@link NatService} detects an external address.
   * Once initialized, the local node record is immediately synchronized to disk.
   *
   * <p>This method must be called before any discovery operations that rely on the local ENR.
   *
   * @param advertisedHost the configured advertised host or IP address
   * @param discoveryPort the UDP discovery port
   * @param tcpPort the TCP listening port
   */
  public void initializeLocalNode(
      final String advertisedHost, final int discoveryPort, final int tcpPort) {

    this.advertisedAddress = natService.queryExternalIPAddress(advertisedHost);

    final DiscoveryPeerV4 self =
        DiscoveryPeerV4.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(nodeId)
                .ipAddress(advertisedAddress)
                .listeningPort(tcpPort)
                .discoveryPort(discoveryPort)
                .build());

    this.localNode = Optional.of(self);
    updateNodeRecord();
  }

  /**
   * Ensures the local {@link NodeRecord} is up to date.
   *
   * <p>If a persisted ENR exists and all relevant fields match the current configuration (node ID,
   * IP address, ports, and fork ID), it is reused as-is.
   *
   * <p>If any field differs, a new ENR is created with an incremented sequence number, signed using
   * the local {@link NodeKey}, and persisted to disk.
   *
   * @throws IllegalStateException if the local node has not been initialized
   */
  public void updateNodeRecord() {
    final NodeRecordFactory factory = NodeRecordFactory.DEFAULT;

    final Optional<NodeRecord> existingRecord =
        variablesStorage.getLocalEnrSeqno().map(factory::fromBytes);

    final Bytes ipAddressBytes = Bytes.of(InetAddresses.forString(advertisedAddress).getAddress());

    final EnodeURL enode =
        localNode
            .map(DiscoveryPeerV4::getEnodeURL)
            .orElseThrow(() -> new IllegalStateException("Local node must be initialized"));

    final int discoveryPort = enode.getDiscoveryPort().orElse(0);
    final int listeningPort = enode.getListeningPort().orElse(0);
    final List<Bytes> forkId = forkIdSupplier.get();

    // Reuse the existing ENR if all relevant fields are unchanged.
    final NodeRecord nodeRecord =
        existingRecord
            .filter(
                record ->
                    nodeId.equals(record.get(EnrField.PKEY_SECP256K1))
                        && ipAddressBytes.equals(record.get(EnrField.IP_V4))
                        && Integer.valueOf(discoveryPort).equals(record.get(EnrField.UDP))
                        && Integer.valueOf(listeningPort).equals(record.get(EnrField.TCP))
                        && forkId.equals(record.get(FORK_ID_ENR_FIELD)))
            // Otherwise, create a new ENR with an incremented sequence number,
            // sign it with the local node key, and persist it to disk.
            .orElseGet(
                () ->
                    createAndPersistNodeRecord(
                        factory,
                        existingRecord,
                        ipAddressBytes,
                        discoveryPort,
                        listeningPort,
                        forkId));

    localNode.get().setNodeRecord(nodeRecord);
  }

  /**
   * Creates, signs, and persists a new {@link NodeRecord}.
   *
   * <p>The sequence number is derived from the existing record (if present) and incremented by one.
   * The record is signed according to the ENR specification using the local node key and written to
   * persistent storage.
   *
   * @param factory the {@link NodeRecordFactory} used to construct the record
   * @param existingRecord the previously persisted ENR, if any
   * @param ipAddressBytes the IPv4 address encoded for the ENR
   * @param discoveryPort the UDP discovery port
   * @param listeningPort the TCP listening port
   * @param forkId the current fork ID for the chain head
   * @return the newly created and persisted {@link NodeRecord}
   */
  private NodeRecord createAndPersistNodeRecord(
      final NodeRecordFactory factory,
      final Optional<NodeRecord> existingRecord,
      final Bytes ipAddressBytes,
      final int discoveryPort,
      final int listeningPort,
      final List<Bytes> forkId) {

    final UInt64 sequence = existingRecord.map(NodeRecord::getSeq).orElse(UInt64.ZERO).add(1);

    final SignatureAlgorithm signatureAlgorithm = SIGNATURE_ALGORITHM.get();

    final NodeRecord record =
        factory.createFromValues(
            sequence,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(
                signatureAlgorithm.getCurveName(),
                signatureAlgorithm.compressPublicKey(signatureAlgorithm.createPublicKey(nodeId))),
            new EnrField(EnrField.IP_V4, ipAddressBytes),
            new EnrField(EnrField.TCP, listeningPort),
            new EnrField(EnrField.UDP, discoveryPort),
            new EnrField(FORK_ID_ENR_FIELD, Collections.singletonList(forkId)));

    record.setSignature(
        nodeKey.sign(Hash.keccak256(record.serializeNoSignature())).encodedBytes().slice(0, 64));

    LOG.info("Writing node record to disk. {}", record);

    final var updater = variablesStorage.updater();
    updater.setLocalEnrSeqno(record.serialize());
    updater.commit();

    return record;
  }
}
