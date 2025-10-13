/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import static org.hyperledger.besu.ethereum.p2p.discovery.dns.KVReader.readKV;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.io.Base32;
import org.apache.tuweni.io.Base64URLSafe;
import org.bouncycastle.math.ec.ECPoint;

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
/** Intermediate format to write DNS entries */
public interface DNSEntry {

  /**
   * Read a DNS entry from a String.
   *
   * @param serialized the serialized form of a DNS entry
   * @return DNS entry if found
   * @throws IllegalArgumentException if the record cannot be read
   */
  static DNSEntry readDNSEntry(final String serialized) {
    final String record = trimQuotes(serialized);
    final String prefix = getPrefix(record);
    return switch (prefix) {
      case "enrtree-root" -> new ENRTreeRoot(readKV(record));
      case "enrtree-branch" -> new ENRTree(record.substring(prefix.length() + 1));
      case "enr" -> new ENRNode(readKV(record));
      case "enrtree" -> new ENRTreeLink(record);
      default ->
          throw new IllegalArgumentException(
              serialized + " should contain enrtree-branch, enr, enrtree-root or enrtree");
    };
  }

  private static String trimQuotes(final String str) {
    if (str.startsWith("\"") && str.endsWith("\"")) {
      return str.substring(1, str.length() - 1);
    }
    return str;
  }

  private static String getPrefix(final String input) {
    final String[] parts = input.split(":", 2);
    return parts.length > 0 ? parts[0] : "";
  }

  /** Represents a node in the ENR record. */
  class ENRNode implements DNSEntry {

    private final EthereumNodeRecord nodeRecord;

    /**
     * Constructs ENRNode with the given attributes.
     *
     * @param attrs the attributes of the node
     */
    public ENRNode(final Map<String, String> attrs) {
      if (attrs == null) {
        throw new IllegalArgumentException("ENRNode attributes cannot be null");
      }
      nodeRecord =
          Optional.ofNullable(attrs.get("enr"))
              .map(Base64URLSafe::decode)
              .map(EthereumNodeRecord::fromRLP)
              .orElseThrow(() -> new IllegalArgumentException("Invalid ENR record"));
    }

    /**
     * Ethereum node record.
     *
     * @return the instance of EthereumNodeRecord
     */
    public EthereumNodeRecord nodeRecord() {
      return nodeRecord;
    }

    @Override
    public String toString() {
      return nodeRecord.toString();
    }
  }

  /** Root of the ENR tree */
  class ENRTreeRoot implements DNSEntry {
    private final String version;
    private final Long seq;
    private final SECP256K1.Signature sig;
    private final String enrRoot;
    private final String linkRoot;

    /**
     * Creates a new ENRTreeRoot
     *
     * @param attrs The attributes of the root
     */
    public ENRTreeRoot(final Map<String, String> attrs) {
      if (attrs == null) {
        throw new IllegalArgumentException("ENRNode attributes cannot be null");
      }

      version =
          Optional.ofNullable(attrs.get("enrtree-root"))
              .orElseThrow(() -> new IllegalArgumentException("Missing attribute enrtree-root"));
      seq =
          Optional.ofNullable(attrs.get("seq"))
              .map(Long::parseLong)
              .orElseThrow(() -> new IllegalArgumentException("Missing attribute seq"));
      sig =
          Optional.ofNullable(attrs.get("sig"))
              .map(Base64URLSafe::decode)
              .map(
                  sigBytes ->
                      SECP256K1.Signature.fromBytes(
                          Bytes.concatenate(
                              sigBytes, Bytes.wrap(new byte[Math.max(0, 65 - sigBytes.size())]))))
              .orElseThrow(() -> new IllegalArgumentException("Missing attribute sig"));
      enrRoot =
          Optional.ofNullable(attrs.get("e"))
              .orElseThrow(() -> new IllegalArgumentException("Missing attribute e"));
      linkRoot =
          Optional.ofNullable(attrs.get("l"))
              .orElseThrow(() -> new IllegalArgumentException("Missing attribute l"));
    }

    /**
     * Gets sequence
     *
     * @return sequence
     */
    public Long seq() {
      return seq;
    }

    /**
     * Link root.
     *
     * @return the link root.
     */
    public String linkRoot() {
      return linkRoot;
    }

    /**
     * ENR root.
     *
     * @return the enr root.
     */
    public String enrRoot() {
      return enrRoot;
    }

    /**
     * Signature.
     *
     * @return SECP256K1 signature
     */
    public SECP256K1.Signature sig() {
      return sig;
    }

    @Override
    public String toString() {
      return String.format(
          "enrtree-root:%s e=%s l=%s seq=%d sig=%s",
          version, enrRoot, linkRoot, seq, Base64URLSafe.encode(sig.bytes()));
    }

    /**
     * Returns the signed content of the root
     *
     * @return the signed content
     */
    public String signedContent() {
      return String.format("enrtree-root:%s e=%s l=%s seq=%d", version, enrRoot, linkRoot, seq);
    }
  }

  /** Represents a branch in the ENR record. */
  class ENRTree implements DNSEntry {
    private final List<String> entries;

    /**
     * Constructs ENRTree with the given entries.
     *
     * @param entriesAsString the entries of the branch
     */
    public ENRTree(final String entriesAsString) {
      entries =
          Arrays.stream(entriesAsString.split("[,\"]"))
              .filter(it -> it.length() > 4)
              .collect(Collectors.toList());
    }

    /**
     * Entries of the branch.
     *
     * @return the entries of the branch
     */
    public List<String> entries() {
      return entries;
    }

    @Override
    public String toString() {
      return "enrtree-branch:" + String.join(",", entries);
    }
  }

  /** Class representing an ENR Tree link */
  class ENRTreeLink implements DNSEntry {
    private final String domainName;
    private final String encodedPubKey;
    private final SECP256K1.PublicKey pubKey;

    /**
     * Creates a new ENRTreeLink
     *
     * @param enrTreeLink The URI representing ENR Tree link
     */
    public ENRTreeLink(final String enrTreeLink) {
      final URI uri = URI.create(enrTreeLink);
      this.domainName = uri.getHost();
      this.encodedPubKey = uri.getUserInfo() == null ? "" : uri.getUserInfo();
      this.pubKey = fromBase32(encodedPubKey);
    }

    private static SECP256K1.PublicKey fromBase32(final String base32) {
      final byte[] keyBytes = Base32.decodeBytes(base32);
      final ECPoint ecPoint = SECP256K1.Parameters.CURVE.getCurve().decodePoint(keyBytes);
      return SECP256K1.PublicKey.fromBytes(Bytes.wrap(ecPoint.getEncoded(false)).slice(1));
    }

    /**
     * Decoded SECP256K1 public key.
     *
     * @return derived SECP256K1.PublicKey
     */
    public SECP256K1.PublicKey publicKey() {
      return pubKey;
    }

    /**
     * Domain name.
     *
     * @return the domain name
     */
    public String domainName() {
      return domainName;
    }

    @Override
    public String toString() {
      return String.format("enrtree://%s@%s", encodedPubKey, domainName);
    }
  }
}
