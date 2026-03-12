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

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.net.InetAddresses;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

/** Shared test utilities for DiscV5 test classes. */
public final class DiscV5TestHelper {

  private static final SignatureAlgorithm SIGNATURE_ALGORITHM =
      SignatureAlgorithmFactory.getInstance();

  private DiscV5TestHelper() {}

  /** Creates a signed IPv4 NodeRecord. */
  public static NodeRecord createSignedNodeRecord(
      final NodeKey nodeKey, final String ip, final int tcp, final int udp) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.TCP, tcp));
    fields.add(new EnrField(EnrField.UDP, udp));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed IPv4 NodeRecord without a TCP port. */
  public static NodeRecord createSignedNodeRecordNoTcp(
      final NodeKey nodeKey, final String ip, final int udp) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.UDP, udp));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed IPv4 NodeRecord with only a TCP port (no explicit UDP). */
  public static NodeRecord createSignedNodeRecordTcpOnly(
      final NodeKey nodeKey, final String ip, final int tcp) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.TCP, tcp));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed IPv6-only NodeRecord. */
  public static NodeRecord createSignedNodeRecordIpv6(
      final NodeKey nodeKey, final String ip6, final int tcp6, final int udp6) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ip6Bytes = Bytes.of(InetAddresses.forString(ip6).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V6, ip6Bytes));
    fields.add(new EnrField(EnrField.TCP_V6, tcp6));
    fields.add(new EnrField(EnrField.UDP_V6, udp6));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed dual-stack NodeRecord with both IPv4 and IPv6 fields. */
  public static NodeRecord createSignedDualStackNodeRecord(
      final NodeKey nodeKey,
      final String ip,
      final int tcp,
      final int udp,
      final String ip6,
      final int tcp6,
      final int udp6) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes ip6Bytes = Bytes.of(InetAddresses.forString(ip6).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.TCP, tcp));
    fields.add(new EnrField(EnrField.UDP, udp));
    fields.add(new EnrField(EnrField.IP_V6, ip6Bytes));
    fields.add(new EnrField(EnrField.TCP_V6, tcp6));
    fields.add(new EnrField(EnrField.UDP_V6, udp6));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed IPv4 NodeRecord with a fork ID ("eth" field). */
  public static NodeRecord createSignedNodeRecordWithForkId(
      final NodeKey nodeKey,
      final String ip,
      final int tcp,
      final int udp,
      final List<Bytes> forkId) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.TCP, tcp));
    fields.add(new EnrField(EnrField.UDP, udp));
    fields.add(new EnrField("eth", Collections.singletonList(forkId)));

    return signRecord(nodeKey, fields);
  }

  /** Creates a signed dual-stack NodeRecord without udp6 (only ip6/tcp6). */
  public static NodeRecord createSignedDualStackNodeRecordNoUdp6(
      final NodeKey nodeKey,
      final String ip,
      final int tcp,
      final int udp,
      final String ip6,
      final int tcp6) {
    final Bytes nodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Bytes ipBytes = Bytes.of(InetAddresses.forString(ip).getAddress());
    final Bytes ip6Bytes = Bytes.of(InetAddresses.forString(ip6).getAddress());
    final Bytes compressedKey =
        SIGNATURE_ALGORITHM.compressPublicKey(SIGNATURE_ALGORITHM.createPublicKey(nodeId));

    final List<EnrField> fields = new ArrayList<>();
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(SIGNATURE_ALGORITHM.getCurveName(), compressedKey));
    fields.add(new EnrField(EnrField.IP_V4, ipBytes));
    fields.add(new EnrField(EnrField.TCP, tcp));
    fields.add(new EnrField(EnrField.UDP, udp));
    fields.add(new EnrField(EnrField.IP_V6, ip6Bytes));
    fields.add(new EnrField(EnrField.TCP_V6, tcp6));

    return signRecord(nodeKey, fields);
  }

  private static NodeRecord signRecord(final NodeKey nodeKey, final List<EnrField> fields) {
    final NodeRecord record = NodeRecordFactory.DEFAULT.createFromValues(UInt64.valueOf(1), fields);
    record.setSignature(
        nodeKey.sign(Hash.keccak256(record.serializeNoSignature())).encodedBytes().slice(0, 64));
    return record;
  }

  /** Returns the optional field value from a NodeRecord, typed. */
  @SuppressWarnings("unchecked")
  static <T> Optional<T> getField(final NodeRecord record, final String key) {
    return Optional.ofNullable((T) record.get(key));
  }
}
