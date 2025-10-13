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

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.DNSEntry.ENRNode;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.DNSEntry.ENRTreeLink;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.DNSEntry.ENRTreeRoot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Splitter;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
/** Resolves a set of ENR nodes from a host name. */
public class DNSResolver {
  private static final Logger LOG = LoggerFactory.getLogger(DNSResolver.class);
  private final String enrLink;
  private long seq;
  private final DnsClient dnsClient;

  /**
   * Creates a new DNSResolver.
   *
   * @param vertx Vertx instance which is used to create DNS Client
   * @param enrLink the ENR link to start with, of the form enrtree://PUBKEY@domain
   * @param seq the sequence number of the root record. If the root record seq is higher, proceed
   *     with visit.
   * @param dnsServer the DNS server to use for DNS query. If empty, the default DNS server will be
   *     used.
   */
  public DNSResolver(
      final Vertx vertx, final String enrLink, final long seq, final Optional<String> dnsServer) {
    this.enrLink = enrLink;
    this.seq = seq;
    final DnsClientOptions dnsClientOptions =
        dnsServer.map(DNSResolver::buildDnsClientOptions).orElseGet(DnsClientOptions::new);
    dnsClient = vertx.createDnsClient(dnsClientOptions);
  }

  private static DnsClientOptions buildDnsClientOptions(final String server) {
    final List<String> hostPort = Splitter.on(":").splitToList(server);
    final DnsClientOptions dnsClientOptions = new DnsClientOptions();
    dnsClientOptions.setHost(hostPort.get(0));
    if (hostPort.size() > 1) {
      try {
        int port = Integer.parseInt(hostPort.get(1));
        dnsClientOptions.setPort(port);
      } catch (NumberFormatException e) {
        LOG.trace("Invalid port number {}, ignoring", hostPort.get(1));
      }
    }
    return dnsClientOptions;
  }

  /**
   * Convenience method to read all ENRs, from a top-level record.
   *
   * @return all ENRs collected
   */
  public List<EthereumNodeRecord> collectAll() {
    final List<EthereumNodeRecord> nodes = new ArrayList<>();
    final DNSVisitor visitor = nodes::add;
    visitTree(new ENRTreeLink(enrLink), visitor);
    if (!nodes.isEmpty()) {
      LOG.debug("Resolved {} nodes from DNS for enr link {}", nodes.size(), enrLink);
    } else {
      LOG.debug("No nodes resolved from DNS");
    }
    return Collections.unmodifiableList(nodes);
  }

  /**
   * Sequence number of the root record.
   *
   * @return the current sequence number of the root record
   */
  public long sequence() {
    return seq;
  }

  /**
   * Reads a complete tree of record, starting with the top-level record.
   *
   * @param link the ENR link to start with
   * @param visitor the visitor that will look at each record
   */
  private void visitTree(final ENRTreeLink link, final DNSVisitor visitor) {
    Optional<DNSEntry> optionalEntry = resolveRecord(link.domainName());
    if (optionalEntry.isEmpty()) {
      LOG.trace("No DNS record found for {}", link.domainName());
      return;
    }

    final DNSEntry dnsEntry = optionalEntry.get();
    if (!(dnsEntry instanceof ENRTreeRoot treeRoot)) {
      LOG.debug("Root entry {} is not an ENR tree root", dnsEntry);
      return;
    }

    if (!checkSignature(treeRoot, link.publicKey(), treeRoot.sig())) {
      LOG.debug("ENR tree root {} failed signature check", link.domainName());
      return;
    }
    if (treeRoot.seq() <= seq) {
      LOG.debug("ENR tree root seq {} is not higher than {}, aborting", treeRoot.seq(), seq);
      return;
    }
    seq = treeRoot.seq();

    internalVisit(treeRoot.enrRoot(), link.domainName(), visitor);
    internalVisit(treeRoot.linkRoot(), link.domainName(), visitor);
  }

  private boolean internalVisit(
      final String entryName, final String domainName, final DNSVisitor visitor) {
    final Optional<DNSEntry> optionalDNSEntry = resolveRecord(entryName + "." + domainName);
    if (optionalDNSEntry.isEmpty()) {
      return true;
    }

    final DNSEntry entry = optionalDNSEntry.get();
    switch (entry) {
      case ENRNode node -> {
        return visitor.visit(node.nodeRecord());
      }
      case DNSEntry.ENRTree tree -> {
        for (String e : tree.entries()) {
          boolean keepGoing = internalVisit(e, domainName, visitor);
          if (!keepGoing) {
            return false;
          }
        }
      }
      case ENRTreeLink link -> visitTree(link, visitor);
      default -> LOG.debug("Unsupported type of node {}", entry);
    }
    return true;
  }

  /**
   * Maps TXT DNS record to DNSEntry.
   *
   * @param domainName the domain name to query
   * @return the DNS entry read from the domain. Empty if no record is found.
   */
  Optional<DNSEntry> resolveRecord(final String domainName) {
    return resolveRawRecord(domainName).map(DNSEntry::readDNSEntry);
  }

  /**
   * Resolves the first TXT record for a domain name and returns it.
   *
   * @param domainName the name of the DNS domain to query
   * @return the first TXT entry of the DNS record. Empty if no record is found.
   */
  Optional<String> resolveRawRecord(final String domainName) {
    LOG.trace("Resolving TXT records on domain: {}", domainName);
    try {
      // Future.await parks current virtual thread and waits for the result. Any failure is
      // thrown as a Throwable.
      return Future.await(dnsClient.resolveTXT(domainName)).stream().findFirst();
    } catch (final Throwable e) {
      LOG.trace("Error while resolving TXT records on domain: {}", domainName, e);
      return Optional.empty();
    }
  }

  private boolean checkSignature(
      final ENRTreeRoot root, final SECP256K1.PublicKey pubKey, final SECP256K1.Signature sig) {
    final Bytes32 hash =
        Hash.keccak256(Bytes.wrap(root.signedContent().getBytes(StandardCharsets.UTF_8)));
    return SECP256K1.verifyHashed(hash, sig, pubKey);
  }
}
