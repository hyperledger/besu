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

public class NodeRecordManager {
  private static final Logger LOG = LoggerFactory.getLogger(NodeRecordManager.class);
  private static final com.google.common.base.Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private Optional<DiscoveryPeer> localNode = Optional.empty();
  private final VariablesStorage variablesStorage;

  private final NodeKey nodeKey;
  private final Bytes id;
  private final Supplier<List<Bytes>> forkIdSupplier;
  final NatService natService;
  private String advertisedAddress;


  public NodeRecordManager(
    final StorageProvider storageProvider,
    final NodeKey nodeKey,
    final ForkIdManager forkIdManager, NatService natService) {
    this.variablesStorage = storageProvider.createVariablesStorage();
    this.nodeKey = nodeKey;
    this.id = nodeKey.getPublicKey().getEncodedBytes();
    this.forkIdSupplier = () -> forkIdManager.getForkIdForChainHead().getForkIdAsBytesList();
    this.natService = natService;
  }

  public Optional<DiscoveryPeer> getLocalNode() {
    return localNode;
  }


  public void initializeLocalNode(final String advertisedHost, final int discoveryPort, final int tcpPort) {
    // override advertised host if we detect an external IP address via NAT manager
    this.advertisedAddress = natService.queryExternalIPAddress(advertisedHost);
    DiscoveryPeer ourNode = DiscoveryPeer.fromEnode(
      EnodeURLImpl.builder()
        .nodeId(id)
        .ipAddress(advertisedAddress)
        .listeningPort(tcpPort)
        .discoveryPort(discoveryPort)
        .build());
    localNode = Optional.of(ourNode);
    updateNodeRecord();
  }

  public void updateNodeRecord() {
    final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
    final Optional<NodeRecord> existingNodeRecord =
        variablesStorage.getLocalEnrSeqno().map(nodeRecordFactory::fromBytes);

    final Bytes addressBytes = Bytes.of(InetAddresses.forString(advertisedAddress).getAddress());
    final Optional<EnodeURL> maybeEnodeURL = localNode.map(DiscoveryPeer::getEnodeURL);
    final Integer discoveryPort = maybeEnodeURL.flatMap(EnodeURL::getDiscoveryPort).orElse(0);
    final Integer listeningPort = maybeEnodeURL.flatMap(EnodeURL::getListeningPort).orElse(0);
    final String forkIdEnrField = "eth";
    final NodeRecord newNodeRecord =
        existingNodeRecord
            .filter(
                nodeRecord ->
                    id.equals(nodeRecord.get(EnrField.PKEY_SECP256K1))
                        && addressBytes.equals(nodeRecord.get(EnrField.IP_V4))
                        && discoveryPort.equals(nodeRecord.get(EnrField.UDP))
                        && listeningPort.equals(nodeRecord.get(EnrField.TCP))
                        && forkIdSupplier.get().equals(nodeRecord.get(forkIdEnrField)))
            .orElseGet(
                () -> {
                  final UInt64 sequenceNumber =
                      existingNodeRecord.map(NodeRecord::getSeq).orElse(UInt64.ZERO).add(1);
                  final NodeRecord nodeRecord =
                      nodeRecordFactory.createFromValues(
                          sequenceNumber,
                          new EnrField(EnrField.ID, IdentitySchema.V4),
                          new EnrField(
                              SIGNATURE_ALGORITHM.get().getCurveName(),
                              SIGNATURE_ALGORITHM
                                  .get()
                                  .compressPublicKey(
                                      SIGNATURE_ALGORITHM.get().createPublicKey(id))),
                          new EnrField(EnrField.IP_V4, addressBytes),
                          new EnrField(EnrField.TCP, listeningPort),
                          new EnrField(EnrField.UDP, discoveryPort),
                          new EnrField(
                              forkIdEnrField, Collections.singletonList(forkIdSupplier.get())));
                  nodeRecord.setSignature(
                      nodeKey
                          .sign(Hash.keccak256(nodeRecord.serializeNoSignature()))
                          .encodedBytes()
                          .slice(0, 64));

                  LOG.info("Writing node record to disk. {}", nodeRecord);
                  final var variablesUpdater = variablesStorage.updater();
                  variablesUpdater.setLocalEnrSeqno(nodeRecord.serialize());
                  variablesUpdater.commit();

                  return nodeRecord;
                });
    localNode
        .orElseThrow(() -> new IllegalStateException("Local node should be set here"))
        .setNodeRecord(newNodeRecord);
  }
}
