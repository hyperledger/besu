/*
 * Copyright ConsenSys AG.
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

import static java.util.Collections.emptyList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

public class TransitionsConfigOptions {

  public static final TransitionsConfigOptions DEFAULT =
      new TransitionsConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode customForkConfigRoot;

  @JsonCreator
  public TransitionsConfigOptions(final ObjectNode customForkConfigRoot) {
    this.customForkConfigRoot = customForkConfigRoot;
  }

  public List<BftFork> getIbftForks() {
    return getBftForks("ibft2", BftFork::new);
  }

  public List<QbftFork> getQbftForks() {
    return getBftForks("qbft", QbftFork::new);
  }

  private <T> List<T> getBftForks(
      final String fieldKey, final Function<ObjectNode, T> forkConstructor) {
    final Optional<ArrayNode> bftForksNode = JsonUtil.getArrayNode(customForkConfigRoot, fieldKey);

    if (bftForksNode.isEmpty()) {
      return emptyList();
    }

    final List<T> bftForks = Lists.newArrayList();

    bftForksNode
        .get()
        .elements()
        .forEachRemaining(
            node -> {
              if (!node.isObject()) {
                throw new IllegalArgumentException("Bft fork is illegally formatted.");
              }
              bftForks.add(forkConstructor.apply((ObjectNode) node));
            });

    return Collections.unmodifiableList(bftForks);
  }
}
