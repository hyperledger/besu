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
package org.hyperledger.besu.config;

import static org.hyperledger.besu.config.JsonUtil.normalizeKey;
import static org.hyperledger.besu.config.JsonUtil.normalizeKeys;

import java.net.URL;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;

interface GenesisReader {
  String CONFIG_FILED = "config";
  String ALLOCATION_FIELD = "alloc";

  ObjectNode getRoot();

  ObjectNode getConfig();

  Stream<GenesisAllocation> streamAllocations();

  class FromObjectNode implements GenesisReader {
    private final ObjectNode root;

    public FromObjectNode(final ObjectNode root) {
      this.root = root;
    }

    @Override
    public ObjectNode getRoot() {
      return normalizeKeys(root);
    }

    @Override
    public ObjectNode getConfig() {
      return normalizeKeys(
          JsonUtil.getObjectNode(root, CONFIG_FILED).orElse(JsonUtil.createEmptyObjectNode()));
    }

    @Override
    public Stream<GenesisAllocation> streamAllocations() {
      return JsonUtil.getObjectNode(root, ALLOCATION_FIELD).stream()
          .flatMap(
              allocations ->
                  Streams.stream(allocations.fieldNames())
                      .map(
                          key ->
                              new GenesisAllocation(
                                  normalizeKey(key),
                                  normalizeKeys(JsonUtil.getObjectNode(allocations, key).get()))));
    }
  }

  class FromURL implements GenesisReader {
    private final URL url;
    private final ObjectNode rootWithoutAllocations;

    public FromURL(final URL url) {
      this.url = url;
      this.rootWithoutAllocations = JsonUtil.objectNodeFromURL(url, false, ALLOCATION_FIELD);
    }

    @Override
    public ObjectNode getRoot() {
      return normalizeKeys(rootWithoutAllocations);
    }

    @Override
    public ObjectNode getConfig() {
      return normalizeKeys(
          JsonUtil.getObjectNode(rootWithoutAllocations, CONFIG_FILED)
              .orElse(JsonUtil.createEmptyObjectNode()));
    }

    @Override
    public Stream<GenesisAllocation> streamAllocations() {
      final var root = JsonUtil.objectNodeFromURL(url, false);
      return JsonUtil.getObjectNode(root, ALLOCATION_FIELD).stream()
          .flatMap(
              allocations ->
                  Streams.stream(allocations.fieldNames())
                      .map(
                          key ->
                              new GenesisAllocation(
                                  normalizeKey(key),
                                  normalizeKeys(JsonUtil.getObjectNode(allocations, key).get()))));
    }
  }
}
