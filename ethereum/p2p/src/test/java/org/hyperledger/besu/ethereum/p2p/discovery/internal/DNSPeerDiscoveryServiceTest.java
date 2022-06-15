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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.DnsClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class DNSPeerDiscoveryServiceTest {

  public static final String TEST_DOMAIN = "enr.test";
  public static final String ENRTREE_REQUEST =
      "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@" + TEST_DOMAIN;
  private static Map<String, List<String>> DNS_TXT_RECORDS;

  @BeforeAll
  static void setup() throws IOException {
    Security.addProvider(new BouncyCastleProvider());
    final Properties properties = new Properties();
    try (InputStream resourceAsStream =
        DNSPeerDiscoveryServiceTest.class.getResourceAsStream("/enr-tree.properties")) {
      properties.load(resourceAsStream);
      DNS_TXT_RECORDS =
          properties.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      e -> e.getKey().toString(),
                      e -> List.of(e.getValue().toString().split("~"))));
    }
  }

  @Test
  void fetchAllNodesAtOnce(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    final int totalLeavesCount = getLeavesCount(getRoot());
    fetchAndCheckRecordsCount(vertx, testContext, dnsClient, totalLeavesCount, totalLeavesCount);
  }

  @Test
  void fetchNodesBeyondLimit(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    final int totalLeavesCount = getLeavesCount(getRoot());
    fetchAndCheckRecordsCount(
        vertx, testContext, dnsClient, totalLeavesCount, totalLeavesCount + 1);
  }

  @Test
  void fetchNodesBeyondLimitWithReset(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient)
      throws InterruptedException {
    mockDefaultDnsClient(vertx, dnsClient);
    final int nodeRecordsCount = getLeavesCount(getRoot());
    final List<NodeRecord> collectedRecords = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch waitInitialRecordsRetrieval = new CountDownLatch(nodeRecordsCount);
    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        nodeRecordsCount,
        nodeRecord -> {
          collectedRecords.add(nodeRecord);
          waitInitialRecordsRetrieval.countDown();
        },
        testContext::failNow);
    waitInitialRecordsRetrieval.await();

    assertThat(dnsStreamer.isExhausted()).isTrue();
    dnsStreamer.reset();

    final int extraRecordsToFetch = 3;
    final CountDownLatch waitExtraRecordsRetrieval = new CountDownLatch(extraRecordsToFetch);
    dnsStreamer.fetchNodeRecords(
        extraRecordsToFetch,
        nodeRecord -> {
          collectedRecords.add(nodeRecord);
          waitExtraRecordsRetrieval.countDown();
        },
        testContext::failNow);
    waitExtraRecordsRetrieval.await();
    testContext.verify(
        () -> {
          assertThat(collectedRecords).hasSize(nodeRecordsCount + extraRecordsToFetch);
          assertThat(collectedRecords.stream().distinct().count()).isEqualTo(nodeRecordsCount);
          testContext.completeNow();
        });
  }

  @Test
  void fetchMultipleTimesUntilRetrieveTheWholeTree(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient)
      throws InterruptedException {
    mockDefaultDnsClient(vertx, dnsClient);
    final int totalLeavesCount = getLeavesCount(getRoot());

    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);

    final int fetchRounds = 7;
    final Collection<NodeRecord> nodeRecords = new ArrayList<>();
    final int expectedRecords = totalLeavesCount / fetchRounds;
    for (int i = 0; i < fetchRounds; i++) {
      final CountDownLatch waitRecordsRetrieval = new CountDownLatch(expectedRecords);
      dnsStreamer.fetchNodeRecords(
          expectedRecords,
          nodeRecord -> {
            nodeRecords.add(nodeRecord);
            waitRecordsRetrieval.countDown();
          },
          testContext::failNow);
      waitRecordsRetrieval.await();
    }

    final VertxTestContext.ExecutionBlock verifyCollectedNodeRecords =
        () -> {
          assertThat(nodeRecords).hasSize(totalLeavesCount);
          assertThat(nodeRecords.stream().distinct().count()).isEqualTo(totalLeavesCount);
          testContext.completeNow();
        };
    final int leavesReminder = totalLeavesCount % fetchRounds;
    if (leavesReminder > 0) {
      final AtomicInteger recordsCount = new AtomicInteger(0);
      dnsStreamer.fetchNodeRecords(
          leavesReminder,
          nodeRecord -> {
            nodeRecords.add(nodeRecord);
            if (recordsCount.incrementAndGet() == leavesReminder) {
              testContext.verify(verifyCollectedNodeRecords);
            }
          },
          testContext::failNow);
    } else {
      testContext.verify(verifyCollectedNodeRecords);
    }
  }

  @Test
  void fetchWithBrokenBranches(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    final List<String> branches = getBranchesAtHeight(getRoot(), 1);
    assumeThat(branches)
        .as(
            "At least 2 branches are needed. One for failing and the other for returning the leaves")
        .hasSizeGreaterThanOrEqualTo(2);
    final List<String> sortedBranches = new ArrayList<>(branches);
    sortedBranches.sort(Comparator.comparing(DNSPeerDiscoveryServiceTest::getLeavesCount));

    final Checkpoint brokenBranchRequested = testContext.checkpoint();
    final String brokenBranch = sortedBranches.get(0);
    when(dnsClient.resolveTXT(anyString()))
        .thenAnswer(
            invocation ->
                vertx.executeBlocking(
                    result -> {
                      final String requestedRecord = invocation.getArgument(0);
                      if (requestedRecord.equals(brokenBranch + "." + TEST_DOMAIN)) {
                        brokenBranchRequested.flag();
                        result.fail(new VertxException("DNS query timeout for " + requestedRecord));
                      } else if (DNS_TXT_RECORDS.containsKey(requestedRecord)) {
                        result.complete(DNS_TXT_RECORDS.get(requestedRecord));
                      } else {
                        result.fail(new VertxException("DNS query timeout for " + requestedRecord));
                      }
                    }));
    final int expectedRecords = getLeavesCount(brokenBranch);
    final Checkpoint fetchedNodes = testContext.checkpoint(expectedRecords);
    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(
            URI.create(ENRTREE_REQUEST),
            dnsClient,
            vertx,
            children -> {
              children.sort(Comparator.comparing(DNSPeerDiscoveryServiceTest::getLeavesCount));
              return children.get(0);
            });
    dnsStreamer.fetchNodeRecords(
        expectedRecords, nodeRecord -> fetchedNodes.flag(), testContext::failNow);
  }

  @Test
  void fetchWithInvalidRoot(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    when(dnsClient.resolveTXT(anyString()))
        .thenAnswer(
            invocation ->
                vertx.executeBlocking(
                    result -> {
                      final String requestedName = invocation.getArgument(0);
                      if (requestedName.equals(TEST_DOMAIN)) {
                        result.complete(
                            List.of("not-an-enrtree-root:v1000 e=NONO l=NONO seq=-42 sig=NONONO"));
                      } else {
                        result.fail("Invalid mock record requested: " + requestedName);
                      }
                    }));

    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        100,
        nodeRecord -> testContext.failNow("Shouldn't return any record"),
        throwable -> {
          testContext.verify(
              () -> {
                assertThat(throwable)
                    .hasMessageStartingWith(
                        "DNS TXT entry for " + TEST_DOMAIN + " does not look like a enrtree-root");
                testContext.completeNow();
              });
        });
  }

  @Test
  void fetchWithWrongValueHash(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    when(dnsClient.resolveTXT(anyString()))
        .thenAnswer(
            invocation ->
                vertx.executeBlocking(
                    result -> {
                      final String requestedName = invocation.getArgument(0);
                      switch (requestedName) {
                        case TEST_DOMAIN:
                          result.complete(
                              List.of(
                                  "enrtree-root:v1 e=YBRAL6BIFN5ZXHRS5WN5SHQVRY l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=3148 sig=Kn28gc8SDlPKjIcLQmg_5R43fPDLqvCzmnm-d0sFlwNP92YdPjILb5lKy40HRjzq9yEiTgCV6JSewZRDTOWlsAE"));
                          return;
                        case "YBRAL6BIFN5ZXHRS5WN5SHQVRY." + TEST_DOMAIN:
                          result.complete(List.of("value not matching hash"));
                          return;
                      }
                      result.fail("Invalid mock record requested: " + requestedName);
                    }));

    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        100,
        nodeRecord -> testContext.failNow("Shouldn't return any record"),
        throwable -> {
          testContext.verify(
              () -> {
                assertThat(throwable)
                    .hasMessageStartingWith("ENR entry doesn't match expected hash");
                testContext.completeNow();
              });
        });
  }

  @Test
  void fetchWithWrongPublicKey(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    mockDefaultDnsClient(vertx, dnsClient);
    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(
            URI.create(
                "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@" + TEST_DOMAIN),
            dnsClient,
            vertx);
    dnsStreamer.fetchNodeRecords(
        100,
        nodeRecord -> testContext.failNow("Shouldn't return any record"),
        throwable -> {
          testContext.verify(
              () -> {
                assertThat(throwable).hasMessageStartingWith("Invalid signature");
                testContext.completeNow();
              });
        });
  }

  @Test
  void fetchWithInvalidPublicKey(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    mockDefaultDnsClient(vertx, dnsClient);
    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(
            URI.create("enrtree://INVALIDPUBLICKEY@" + TEST_DOMAIN), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        100,
        nodeRecord -> testContext.failNow("Shouldn't return any record"),
        throwable -> {
          testContext.verify(
              () -> {
                assertThat(throwable).hasMessageStartingWith("Invalid point encoding");
                testContext.completeNow();
              });
        });
  }

  @Test
  void fetchWithFailureAtRoot(
      final Vertx vertx, final VertxTestContext testContext, @Mock final DnsClient dnsClient) {
    when(dnsClient.resolveTXT(anyString()))
        .thenAnswer(
            invocation ->
                vertx.executeBlocking(
                    result -> {
                      result.fail(
                          new VertxException("DNS query timeout for " + invocation.getArgument(0)));
                    }));

    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        100,
        nodeRecord -> testContext.failNow("Shouldn't return any record"),
        throwable -> {
          testContext.verify(
              () -> {
                assertThat(throwable).hasMessageStartingWith("DNS query timeout for");
                testContext.completeNow();
              });
        });
  }

  private static void fetchAndCheckRecordsCount(
      final Vertx vertx,
      final VertxTestContext testContext,
      final DnsClient dnsClient,
      final int nodesExpected,
      final int nodesRequested) {
    mockDefaultDnsClient(vertx, dnsClient);
    final Checkpoint fetchedNodes = testContext.checkpoint(nodesExpected);
    final DNSPeerDiscoveryService dnsStreamer =
        new DNSPeerDiscoveryService(URI.create(ENRTREE_REQUEST), dnsClient, vertx);
    dnsStreamer.fetchNodeRecords(
        nodesRequested, nodeRecord -> fetchedNodes.flag(), testContext::failNow);
  }

  private static void mockDefaultDnsClient(final Vertx vertx, final DnsClient dnsClient) {
    when(dnsClient.resolveTXT(anyString()))
        .thenAnswer(
            invocation ->
                vertx.executeBlocking(
                    result -> {
                      final String requestedRecord = invocation.getArgument(0);
                      if (DNS_TXT_RECORDS.containsKey(requestedRecord)) {
                        result.complete(DNS_TXT_RECORDS.get(requestedRecord));
                      } else {
                        result.fail(new VertxException("DNS query timeout for " + requestedRecord));
                      }
                    }));
  }

  private static String getRoot() {
    return String.join("", DNS_TXT_RECORDS.get(DNSPeerDiscoveryServiceTest.TEST_DOMAIN))
        .split(" ", -1)[1]
        .split("=", -1)[1];
  }

  private static int getLeavesCount(final String root) {
    final String child = String.join("", DNS_TXT_RECORDS.get(root + "." + TEST_DOMAIN));
    if (child.startsWith(DNSPeerDiscoveryService.ENR_LEAF_TXT_PREFIX)) {
      return 1;
    } else {
      return Stream.of(
              child
                  .substring(DNSPeerDiscoveryService.ENR_BRANCH_TXT_PREFIX.length())
                  .split(",", -1))
          .mapToInt(DNSPeerDiscoveryServiceTest::getLeavesCount)
          .sum();
    }
  }

  private static List<String> getBranchesAtHeight(final String root, final int height) {
    if (height == 0) {
      return List.of(root);
    } else {
      final String heightValue = String.join("", DNS_TXT_RECORDS.get(root + "." + TEST_DOMAIN));
      if (!heightValue.startsWith(DNSPeerDiscoveryService.ENR_BRANCH_TXT_PREFIX)) {
        throw new IllegalArgumentException("There is no branch at height " + height);
      }
      ;
      final String[] branches =
          heightValue
              .substring(DNSPeerDiscoveryService.ENR_BRANCH_TXT_PREFIX.length())
              .split(",", -1);
      return Stream.of(branches)
          .map(branch -> getBranchesAtHeight(branch, height - 1))
          .flatMap(List::stream)
          .collect(Collectors.toList());
    }
  }
}
