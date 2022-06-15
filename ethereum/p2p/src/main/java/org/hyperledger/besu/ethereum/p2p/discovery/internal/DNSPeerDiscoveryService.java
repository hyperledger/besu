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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.DnsClient;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.io.Base32;
import org.apache.tuweni.io.Base64URLSafe;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DNSPeerDiscoveryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DNSPeerDiscoveryService.class);
  private static final Pattern ENR_ROOT_TXT_PATTERN =
      Pattern.compile(
          "(?<signedcontent>enrtree-root:v1 e=(?<enrroot>\\S*) l=(\\S*) seq=(?<sequence>\\d*)) sig=(?<signature>\\S*)");
  public static final String ENR_BRANCH_TXT_PREFIX = "enrtree-branch:";
  public static final String ENR_LEAF_TXT_PREFIX = "enr:";

  private final URI dnsDiscoveryUri;
  private final DnsClient dnsClient;
  private final Function<List<String>, String> nextElementSelector;
  private final Vertx vertx;
  private final Deque<List<String>> treeLevels;
  private int lastSequence;
  private long timerId;

  public DNSPeerDiscoveryService(
      final URI dnsDiscoveryUri, final DnsClient dnsClient, final Vertx vertx) {
    this(
        dnsDiscoveryUri,
        dnsClient,
        vertx,
        branches -> {
          final int randomBranch = ThreadLocalRandom.current().nextInt(branches.size());
          return branches.get(randomBranch);
        });
  }

  DNSPeerDiscoveryService(
      final URI dnsDiscoveryUri,
      final DnsClient dnsClient,
      final Vertx vertx,
      final Function<List<String>, String> nextElementSelector) {
    this.dnsDiscoveryUri = dnsDiscoveryUri;
    this.dnsClient = dnsClient;
    this.nextElementSelector = nextElementSelector;
    this.vertx = vertx;
    this.treeLevels = new ArrayDeque<>();
  }

  public void start(
      final long period,
      final int recordsPerPeriod,
      final Consumer<NodeRecord> nodeRecordConsumer,
      final Consumer<Throwable> exceptionHandler) {
    fetchNodeRecords(recordsPerPeriod, nodeRecordConsumer, exceptionHandler);
    timerId =
        vertx.setPeriodic(
            period,
            (timerId) -> fetchNodeRecords(recordsPerPeriod, nodeRecordConsumer, exceptionHandler));
  }

  public void stop() {
    vertx.cancelTimer(timerId);
  }

  public void reset() {
    lastSequence = 0;
  }

  public boolean isExhausted() {
    return treeLevels.isEmpty();
  }

  void fetchNodeRecords(
      final int requestedRecords,
      final Consumer<NodeRecord> nodeRecordConsumer,
      final Consumer<Throwable> exceptionHandler) {
    final String enrDescriptorBaseName = dnsDiscoveryUri.getHost();
    dnsClient
        .resolveTXT(enrDescriptorBaseName)
        .onFailure(exceptionHandler::accept)
        .map(DNSPeerDiscoveryService::singleEntry)
        .onFailure(exceptionHandler::accept)
        .onSuccess(
            enrDescriptor -> {
              try {
                final Matcher m = ENR_ROOT_TXT_PATTERN.matcher(enrDescriptor);
                if (!m.matches()) {
                  throw new IllegalStateException(
                      String.format(
                          "DNS TXT entry for %s does not look like a enrtree-root: '%s'",
                          enrDescriptorBaseName, enrDescriptor));
                }
                checkSignature(
                    dnsDiscoveryUri.getUserInfo(), m.group("signedcontent"), m.group("signature"));
                final int currentSequence = Integer.parseInt(m.group("sequence"));
                if (currentSequence != lastSequence) {
                  lastSequence = currentSequence;
                  treeLevels.clear();
                  final String enrTreeRoot = m.group("enrroot");
                  treeLevels.push(new ArrayList<>(Collections.singletonList(enrTreeRoot)));
                }
                visitNextElement(
                    enrDescriptorBaseName,
                    nodeRecordConsumer,
                    exceptionHandler,
                    0,
                    requestedRecords);
              } catch (RuntimeException ex) {
                exceptionHandler.accept(ex);
              }
            });
  }

  private void visitNextElement(
      final String baseName,
      final Consumer<NodeRecord> nodeRecordConsumer,
      final Consumer<Throwable> exceptionHandler,
      final int recordsFound,
      final int maxRecords) {
    if (treeLevels.isEmpty()) {
      LOGGER.warn("All records have been returned");
      return;
    }
    final List<String> branches = treeLevels.peek();
    String nextBranch = nextElementSelector.apply(branches);
    branches.remove(nextBranch);
    if (branches.isEmpty()) {
      treeLevels.pop();
    }
    final String nextBranchName = String.format("%s.%s", nextBranch, baseName);
    dnsClient
        .resolveTXT(nextBranchName)
        .onFailure(
            ex -> {
              if (ex instanceof VertxException && !treeLevels.isEmpty()) {
                visitNextElement(
                    baseName, nodeRecordConsumer, exceptionHandler, recordsFound, maxRecords);
              } else {
                exceptionHandler.accept(ex);
              }
            })
        .map(DNSPeerDiscoveryService::singleEntry)
        .onSuccess(
            traverseTreeHandler(
                baseName,
                nextBranch,
                nodeRecordConsumer,
                exceptionHandler,
                recordsFound,
                maxRecords));
  }

  private Handler<String> traverseTreeHandler(
      final String baseName,
      final String b32ExpectedHash,
      final Consumer<NodeRecord> nodeRecordConsumer,
      final Consumer<Throwable> exceptionHandler,
      final int recordsFound,
      final int maxRecords) {
    return nodeValue -> {
      try {
        checkHashPrefix(nodeValue, b32ExpectedHash);
        if (nodeValue.startsWith(ENR_BRANCH_TXT_PREFIX)) {
          final List<String> nextBranches =
              new ArrayList<>(
                  List.of(nodeValue.substring(ENR_BRANCH_TXT_PREFIX.length()).split(",")));
          treeLevels.push(nextBranches);
          visitNextElement(
              baseName, nodeRecordConsumer, exceptionHandler, recordsFound, maxRecords);
        } else if (nodeValue.startsWith(ENR_LEAF_TXT_PREFIX)) {
          final NodeRecord nodeRecord =
              NodeRecordFactory.DEFAULT.fromBase64(
                  nodeValue.substring(ENR_LEAF_TXT_PREFIX.length()));
          try {
            nodeRecordConsumer.accept(nodeRecord);
          } catch (Exception ex) {
            LOGGER.error("Error on nodeRecordConsumer execution. Continuing with next element", ex);
          }
          if (recordsFound + 1 < maxRecords && !treeLevels.isEmpty()) {
            visitNextElement(
                baseName, nodeRecordConsumer, exceptionHandler, recordsFound + 1, maxRecords);
          }
        } else {
          throw new IllegalStateException(
              String.format("Bad ENR entry at %s: %s", baseName, nodeValue));
        }
      } catch (Exception ex) {
        exceptionHandler.accept(ex);
      }
    };
  }

  private static void checkHashPrefix(final String value, final String b32ExpectedHashPrefix) {
    final Bytes expectedHash = Base32.decode(b32ExpectedHashPrefix);
    final Bytes actualHash = Bytes.wrap(Hash.keccak256(value.getBytes(StandardCharsets.UTF_8)));
    if (actualHash.commonPrefixLength(expectedHash) != expectedHash.size()) {
      LOGGER.error(
          "ENR entry doesn't match expected hash. ENR entry: {}. Expected hash: {}, actual hash: {}",
          value,
          expectedHash.toHexString(),
          actualHash.toHexString());
      throw new IllegalArgumentException("ENR entry doesn't match expected hash");
    }
  }

  private static void checkSignature(
      final String pubKey, final String signedContent, final String signature) {
    final SECP256K1.PublicKey enrSignerPublicKey = getPublicKey(pubKey);
    final byte[] hash = Hash.keccak256(signedContent.getBytes(StandardCharsets.UTF_8));
    final Bytes sigBytes = Base64URLSafe.decode(signature);
    final SECP256K1.Signature sig = SECP256K1.Signature.fromBytes(sigBytes);
    if (!SECP256K1.verifyHashed(hash, sig, enrSignerPublicKey)) {
      throw new IllegalStateException("Invalid signature");
    }
  }

  private static String singleEntry(final List<String> entries) {
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("Empty DNS TXT entry");
    } else {
      // https://datatracker.ietf.org/doc/html/rfc4408#section-3.1.3
      return String.join("", entries);
    }
  }

  private static SECP256K1.PublicKey getPublicKey(final String pubKey) {
    final byte[] keyBytes = Base32.decodeBytes(pubKey);
    final ECPoint ecPoint = SECP256K1.Parameters.CURVE.getCurve().decodePoint(keyBytes);
    return SECP256K1.PublicKey.fromBytes(Bytes.wrap(ecPoint.getEncoded(false)).slice(1));
  }
}
