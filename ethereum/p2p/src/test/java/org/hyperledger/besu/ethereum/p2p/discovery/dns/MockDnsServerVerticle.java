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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mock DNS server verticle. */
public class MockDnsServerVerticle extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(MockDnsServerVerticle.class);
  private final Map<String, String> txtRecords = new HashMap<>();
  private int dnsPort;

  @Override
  public void start(final Promise<Void> startPromise) throws Exception {
    final DatagramSocket datagramSocket = vertx.createDatagramSocket(new DatagramSocketOptions());
    datagramSocket.handler(packet -> handleDatagramPacket(datagramSocket, packet));

    final String dnsEntriesJsonPath =
        Path.of(Resources.getResource("discovery/dns/dns-records.json").toURI()).toString();
    LOG.debug("Reading DNS entries from: {}", dnsEntriesJsonPath);
    vertx
        .fileSystem()
        .readFile(dnsEntriesJsonPath)
        .compose(
            buffer -> {
              final JsonObject dnsEntries = new JsonObject(buffer.toString());
              final Map<String, Object> jsonMap = dnsEntries.getMap();
              jsonMap.forEach((key, value) -> txtRecords.put(key, value.toString()));

              // start the server
              return datagramSocket.listen(0, "127.0.0.1");
            })
        .onComplete(
            res -> {
              if (res.succeeded()) {
                LOG.info("Mock Dns Server is now listening {}", res.result().localAddress());
                dnsPort = res.result().localAddress().port();
                startPromise.complete();
              } else {
                startPromise.fail(res.cause());
              }
            });
  }

  @Override
  public void stop() {
    LOG.info("Stopping Mock DNS Server");
  }

  private void handleDatagramPacket(final DatagramSocket socket, final DatagramPacket packet) {
    LOG.debug("Packet Received");
    Buffer data = packet.data();
    final short queryId = getQueryId(data);
    final String queryName = extractQueryName(data.getBytes());

    final Buffer response;
    if (txtRecords.containsKey(queryName)) {
      LOG.debug("Query name found {}", queryName);
      response = createTXTResponse(queryId, queryName, txtRecords.get(queryName));
    } else {
      LOG.debug("Query name not found: {}", queryName);
      response = createErrorResponse(queryId, queryName);
    }

    socket.send(response, packet.sender().port(), packet.sender().host());
  }

  private String extractQueryName(final byte[] buffer) {
    StringBuilder queryName = new StringBuilder();
    int index = 12; // Skip the DNS header

    while (index < buffer.length) {
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

      if (index < buffer.length && buffer[index] != 0) {
        queryName.append(".");
      }
    }

    return queryName.toString();
  }

  private Buffer createTXTResponse(
      final short queryId, final String queryName, final String txtRecord) {
    final Buffer buffer = Buffer.buffer();

    // Write DNS header
    buffer.appendShort(queryId); // Query Identifier
    buffer.appendShort((short) 0x8180); // Flags (Standard query response, No error)
    buffer.appendShort((short) 1); // Questions count
    buffer.appendShort((short) 1); // Answers count
    buffer.appendShort((short) 0); // Authority RRs count
    buffer.appendShort((short) 0); // Additional RRs count

    // Write query name
    final Iterable<String> queryLabels = Splitter.on(".").split(queryName);
    for (String label : queryLabels) {
      buffer.appendByte((byte) label.length());
      buffer.appendString(label);
    }
    buffer.appendByte((byte) 0); // End of query name

    // Write query type and class
    buffer.appendShort((short) 16); // Type (TXT)
    buffer.appendShort((short) 1); // Class (IN)

    // Write answer
    for (String label : queryLabels) {
      buffer.appendByte((byte) label.length());
      buffer.appendString(label.toLowerCase(Locale.ROOT));
    }
    buffer.appendByte((byte) 0); // End of answer name

    buffer.appendShort((short) 16); // TXT record type
    buffer.appendShort((short) 1); // Class (IN)
    buffer.appendInt(60); // TTL (60 seconds)

    int txtRecordsLength = txtRecord.getBytes(UTF_8).length;
    buffer.appendShort((short) (txtRecordsLength + 1)); // Data length
    buffer.appendByte((byte) txtRecordsLength); // TXT record length
    buffer.appendString(txtRecord);

    return buffer;
  }

  private Buffer createErrorResponse(final short queryId, final String queryName) {
    Buffer buffer = Buffer.buffer();

    // Write DNS header
    buffer.appendShort(queryId); // Query Identifier
    buffer.appendShort((short) 0x8183); // Flags (Standard query response, NXDOMAIN error)
    buffer.appendShort((short) 1); // Questions count
    buffer.appendShort((short) 0); // Answers count
    buffer.appendShort((short) 0); // Authority RRs count
    buffer.appendShort((short) 0); // Additional RRs count

    // Write query name
    for (String label : Splitter.on(".").split(queryName)) {
      buffer.appendByte((byte) label.length());
      buffer.appendString(label);
    }
    buffer.appendByte((byte) 0); // End of query name

    // Write query type and class
    buffer.appendShort((short) 16); // Type (TXT)
    buffer.appendShort((short) 1); // Class (IN)

    return buffer;
  }

  private short getQueryId(final Buffer queryData) {
    return (short) ((queryData.getByte(0) & 0xff) << 8 | (queryData.getByte(1) & 0xff));
  }

  /**
   * Mock server local port
   *
   * @return server port
   */
  public int port() {
    return dnsPort;
  }
}
