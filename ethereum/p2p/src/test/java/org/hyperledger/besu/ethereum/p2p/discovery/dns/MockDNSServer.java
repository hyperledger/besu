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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Mock DNS Server that returns fixed TXT entries. */
public class MockDNSServer {
  private static final Logger LOG = LoggerFactory.getLogger(MockDNSServer.class);
  private static final int MAX_PACKET_SIZE = 512;

  private final Map<String, String> txtRecords = new HashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private ExecutorService executorService;
  private int dnsPort;

  /** Create an instance of MockDNSServer. Add TXT records that can be served by this server. */
  public MockDNSServer() {

    try {
      final Map<String, String> dnsEntries =
          new ObjectMapper()
              .readValue(
                  Resources.getResource("discovery/dns/dns-records.json"),
                  new TypeReference<>() {});
      txtRecords.putAll(dnsEntries);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Stops the mock DNS server. */
  public void stop() {
    if (started.compareAndSet(true, false)) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    } else {
      LOG.warn("Mock DNS server is not running");
    }
  }

  /** Starts the mock DNS server */
  public void start() {
    if (started.compareAndSet(false, true)) {
      startServer();
    } else {
      LOG.warn("Mock DNS server is already running");
    }
  }

  /**
   * Mock server local port
   *
   * @return server port
   */
  public int port() {
    return dnsPort;
  }

  private void startServer() {
    executorService = Executors.newSingleThreadExecutor();
    executorService.execute(
        () -> {
          try (DatagramSocket socket = new DatagramSocket(0)) {
            dnsPort = socket.getLocalPort();
            LOG.info("Mock DNS server started on port {}", dnsPort);

            while (started.get()) {
              byte[] buffer = new byte[MAX_PACKET_SIZE];
              DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
              socket.receive(packet);

              String queryName = extractQueryName(buffer, packet.getLength());
              String txtRecord = txtRecords.get(queryName);

              byte[] response;
              if (txtRecord != null) {
                response = createTXTResponse(buffer, queryName, txtRecord);
              } else {
                response = createErrorResponse(buffer, queryName);
              }

              final DatagramPacket responsePacket =
                  new DatagramPacket(
                      response, response.length, packet.getAddress(), packet.getPort());
              socket.send(responsePacket);
            }
          } catch (final IOException e) {
            throw new UncheckedIOException("Unexpected IO Exception", e);
          }
          LOG.info("Mock DNS server stopped.");
        });
  }

  private String extractQueryName(final byte[] buffer, final int length) {
    StringBuilder queryName = new StringBuilder();
    int index = 12; // Skip the DNS header

    while (index < length) {
      int labelLength = buffer[index] & 0xFF;

      if (labelLength == 0) {
        break;
      }

      index++;

      for (int i = 0; i < labelLength; i++) {
        char c = (char) (buffer[index + i] & 0xFF);
        queryName.append(c);
      }

      index += labelLength;

      if (index < length && buffer[index] != 0) {
        queryName.append(".");
      }
    }

    return queryName.toString();
  }

  private byte[] createTXTResponse(
      final byte[] queryData, final String queryName, final String txtRecord) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {

      // Write DNS header
      dataOutputStream.writeShort(getQueryId(queryData)); // Identifier (based on query)
      dataOutputStream.writeShort(0x8180); // Flags (Standard query response, No error)
      dataOutputStream.writeShort(1); // Questions count
      dataOutputStream.writeShort(1); // Answers count
      dataOutputStream.writeShort(0); // Authority RRs count
      dataOutputStream.writeShort(0); // Additional RRs count

      // Write query name
      Iterable<String> queryLabels = Splitter.on(".").split(queryName);
      for (String label : queryLabels) {
        dataOutputStream.writeByte(label.length());
        dataOutputStream.writeBytes(label);
      }
      dataOutputStream.writeByte(0); // End of query name

      // Write query type and class
      dataOutputStream.writeShort(16); // Type (TXT)
      dataOutputStream.writeShort(1); // Class (IN)

      // Write answer
      for (String label : queryLabels) {
        dataOutputStream.writeByte(label.length());
        dataOutputStream.writeBytes(label.toLowerCase(Locale.ROOT));
      }
      dataOutputStream.writeByte(0); // End of answer name

      dataOutputStream.writeShort(16); // Type (TXT)
      dataOutputStream.writeShort(1); // Class (IN)
      dataOutputStream.writeInt(60); // TTL (60 seconds)

      byte[] txtRecordBytes = txtRecord.getBytes(UTF_8);
      dataOutputStream.writeShort(txtRecordBytes.length + 1); // Data length
      dataOutputStream.writeByte(txtRecordBytes.length); // TXT record length
      dataOutputStream.write(txtRecordBytes); // TXT record data

      dataOutputStream.flush();
      return outputStream.toByteArray();
    } catch (final IOException e) {
      throw new UncheckedIOException("Unexpected IO Exception", e);
    }
  }

  private byte[] createErrorResponse(final byte[] queryData, final String queryName) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {

      // Write DNS header
      dataOutputStream.writeShort(getQueryId(queryData)); // Identifier (random)
      dataOutputStream.writeShort(0x8183); // Flags (Standard query response, NXDOMAIN error)
      dataOutputStream.writeShort(1); // Questions count
      dataOutputStream.writeShort(0); // Answers count
      dataOutputStream.writeShort(0); // Authority RRs count
      dataOutputStream.writeShort(0); // Additional RRs count

      // Write query name
      for (String label : Splitter.on(".").split(queryName)) {
        dataOutputStream.writeByte(label.length());
        dataOutputStream.writeBytes(label);
      }
      dataOutputStream.writeByte(0); // End of query name

      // Write query type and class
      dataOutputStream.writeShort(16); // Type (TXT)
      dataOutputStream.writeShort(1); // Class (IN)

      dataOutputStream.flush();
      return outputStream.toByteArray();
    } catch (final IOException e) {
      throw new UncheckedIOException("Unexpected IO Exception", e);
    }
  }

  private short getQueryId(final byte[] queryData) {
    return (short) ((queryData[0] & 0xff) << 8 | (queryData[1] & 0xff));
  }
}
