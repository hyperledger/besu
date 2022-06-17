/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.plugin.services.storage.externaldb.client;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.externaldb.configuration.ExternalDbConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;

public class ExternalDBClientKeyValueStorage implements SegmentedKeyValueStorage<String> {

  private final HttpClient httpClient;
  private final ExternalDbConfiguration configuration;
  private final ObjectMapper objectMapper = new ObjectMapper();
  // TODO get this from the config
  private final Duration timeout = Duration.of(30, ChronoUnit.SECONDS);

  public ExternalDBClientKeyValueStorage(
      final HttpClient httpClient, final ExternalDbConfiguration configuration) {
    this.httpClient = httpClient;
    this.configuration = configuration;
  }

  @Override
  public String getSegmentIdentifierByName(final SegmentIdentifier segment) {
    return segment.getName();
  }

  @Override
  public Optional<byte[]> get(final String segment, final byte[] key) throws StorageException {
    try {
      final String dbKey = Bytes.of(key).toHexString();
      final JsonRpcRequest rpcRequest =
          new JsonRpcRequest("2.0", "database_getValue", new String[] {segment, dbKey});
      final String body = objectMapper.writeValueAsString(rpcRequest);
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(configuration.getEndpoint().toURI())
              .timeout(timeout)
              .POST(BodyPublishers.ofString(body))
              .build();
      final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        final ObjectNode rpcResponse = JsonUtil.objectNodeFromString(response.body());
        final Optional<String> result = JsonUtil.getString(rpcResponse, "result");
        return result.map(v -> Bytes.fromHexString(v).toArrayUnsafe());
      } else {
        throw new StorageException("Failed retrieving key " + dbKey + " from storage");
      }
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction<String> startTransaction() throws StorageException {
    return new ExternalDBClientTransaction();
  }

  @Override
  public Stream<byte[]> streamKeys(final String segmentHandle) {
    return Stream.empty();
  }

  @Override
  public boolean tryDelete(final String segmentHandle, final byte[] key) throws StorageException {
    return false;
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final String segmentHandle, final Predicate<byte[]> returnCondition) {
    return Set.of();
  }

  @Override
  public void clear(final String segmentHandle) {}

  @Override
  public void close() throws IOException {}
}
