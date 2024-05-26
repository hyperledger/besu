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
import java.util.function.Consumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.tuweni.devp2p.EthereumNodeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
// TODO: Deploy DNSDaemon as a worker verticle ??
/** Resolves DNS records over time, refreshing records. */
public class DNSDaemon extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(DNSDaemon.class);
  private final String enrLink;
  private final long seq;
  private final long period;
  private final String dnsServer;
  private long periodicTaskId;
  private final Optional<DNSDaemonListener> listener;

  /**
   * Creates a new DNSDaemon.
   *
   * @param enrLink the ENR link to start with, of the form enrtree://PUBKEY@domain
   * @param listener Listener notified when records are read and whenever they are updated.
   * @param seq the sequence number of the root record. If the root record seq is higher, proceed
   *     with visit.
   * @param period the period at which to poll DNS records
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
  }

  private void updateRecords(final List<EthereumNodeRecord> records) {
    listener.ifPresent(it -> it.newRecords(seq, records));
  }

  /** Starts the DNSDaemon. */
  @Override
  public void start() {
    LOG.info("Starting DNSDaemon for {}", enrLink);
    DNSTimerTask task = new DNSTimerTask(vertx, seq, enrLink, this::updateRecords, dnsServer);
    // Use Vertx to run periodic task (TODO: Can we use a worker verticle instead?)
    if (period > 0) {
      this.periodicTaskId = vertx.setPeriodic(period, task);
    } else {
      task.handle(0L);
    }
  }

  /** Stops the DNSDaemon. */
  @Override
  public void stop() {
    if (period > 0) {
      vertx.cancelTimer(this.periodicTaskId);
    } // otherwise we didn't start the timer
  }
}

/** Task that periodically reads DNS records. */
class DNSTimerTask implements Handler<Long> {
  private static final Logger LOG = LoggerFactory.getLogger(DNSTimerTask.class);
  private final String enrLink;
  private final Consumer<List<EthereumNodeRecord>> records;
  private final DNSResolver resolver;

  /**
   * Creates a new DNSTimerTask.
   *
   * @param vertx Instance of Vertx
   * @param seq the sequence number of the root record. If the root record seq is higher, proceed
   *     with visit.
   * @param enrLink the ENR link to start with, of the form enrtree://PUBKEY@domain
   * @param records Consumer that accepts the records read
   * @param dnsServer the DNS server to use for DNS query. If null, the default DNS server will be
   *     used.
   */
  public DNSTimerTask(
      final Vertx vertx,
      final long seq,
      final String enrLink,
      final Consumer<List<EthereumNodeRecord>> records,
      final String dnsServer) {
    this.enrLink = enrLink;
    this.records = records;
    resolver = new DNSResolver(dnsServer, seq, vertx);
  }

  @Override
  public void handle(final Long taskId) {
    LOG.debug("Refreshing DNS records for {}", enrLink);
    // TODO: Does following need to wrap in executeBlock??
    // measure time taken to call resolver.collectAll
    final long startTime = System.nanoTime();
    final List<EthereumNodeRecord> ethereumNodeRecords = resolver.collectAll(enrLink);
    final long endTime = System.nanoTime();
    LOG.info("Time taken to DNSResolver.collectAll: {} ms", (endTime - startTime) / 1_000_000);
    records.accept(ethereumNodeRecords);
  }
}
