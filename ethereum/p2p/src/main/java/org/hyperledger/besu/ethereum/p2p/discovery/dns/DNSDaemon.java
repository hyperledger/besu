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

import java.util.List;
import java.util.Optional;

import io.vertx.core.AbstractVerticle;
import org.apache.tuweni.devp2p.EthereumNodeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
/**
 * Resolves DNS records over time, refreshing records. This is written as a Vertx Verticle which
 * allows to run outside the Vertx event loop
 */
public class DNSDaemon extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(DNSDaemon.class);
  private final String enrLink;
  private final long seq;
  private final long period;
  private final String dnsServer;
  private long periodicTaskId;
  private final Optional<DNSDaemonListener> listener;
  private final DNSResolver dnsResolver;

  /**
   * Creates a new DNSDaemon.
   *
   * @param enrLink the ENR link to start with, of the form enrtree://PUBKEY@domain
   * @param listener Listener notified when records are read and whenever they are updated.
   * @param seq the sequence number of the root record. If the root record seq is higher, proceed
   *     with visit.
   * @param period the period at which to poll DNS records. If negative or zero, it runs only once.
   * @param dnsServer the DNS server to use for DNS query. If null, the default DNS server will be
   *     used.
   */
  public DNSDaemon(
      final String enrLink,
      final DNSDaemonListener listener,
      final long seq,
      final long period,
      final String dnsServer) {
    this.enrLink = enrLink;
    this.listener = Optional.ofNullable(listener);
    this.seq = seq;
    this.period = period;
    this.dnsServer = dnsServer;
    dnsResolver = new DNSResolver(vertx, enrLink, seq, dnsServer);
  }

  /**
   * Callback method to update the listeners with resolved enr records.
   *
   * @param records List of resolved Ethereum Node Records.
   */
  private void updateRecords(final List<EthereumNodeRecord> records) {
    listener.ifPresent(it -> it.newRecords(seq, records));
  }

  /** Starts the DNSDaemon. */
  @Override
  public void start() {
    LOG.info("Starting DNSDaemon for {}", enrLink);
    // Use Vertx to run periodic task if period is set
    if (period > 0) {
      this.periodicTaskId = vertx.setPeriodic(period, this::refreshENRRecords);
    } else {
      refreshENRRecords(0L);
    }
  }

  /** Stops the DNSDaemon. */
  @Override
  public void stop() {
    if (period > 0) {
      vertx.cancelTimer(this.periodicTaskId);
    } // otherwise we didn't start the timer
    // TODO: Call dnsResolver stop
  }

  /**
   * Refresh enr records by calling dnsResolver and updating the listeners.
   *
   * @param taskId the task id of the periodic task
   */
  void refreshENRRecords(final Long taskId) {
    LOG.debug("Refreshing DNS records");
    final long startTime = System.nanoTime();
    final List<EthereumNodeRecord> ethereumNodeRecords = dnsResolver.collectAll();
    final long endTime = System.nanoTime();
    LOG.debug("Time taken to DNSResolver.collectAll: {} ms", (endTime - startTime) / 1_000_000);
    updateRecords(ethereumNodeRecords);
  }
}
