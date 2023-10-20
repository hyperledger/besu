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

/** The Transitions config options. */
public class TransitionsConfigOptions {

  /** The constant DEFAULT. */
  public static final TransitionsConfigOptions DEFAULT =
      new TransitionsConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode customForkConfigRoot;

  /**
   * Instantiates a new Transitions config options.
   *
   * @param customForkConfigRoot the custom fork config root
   */
  @JsonCreator
  public TransitionsConfigOptions(final ObjectNode customForkConfigRoot) {
    this.customForkConfigRoot = customForkConfigRoot;
  }

  /**
   * Gets ibft forks.
   *
   * @return the ibft forks
   */
  public List<BftFork> getIbftForks() {
    return getForks("ibft2", BftFork::new);
  }

  /**
   * Gets qbft forks.
   *
   * @return the qbft forks
   */
  public List<QbftFork> getQbftForks() {
    return getForks("qbft", QbftFork::new);
  }

  /**
   * Gets clique forks.
   *
   * @return the clique forks
   */
  public List<CliqueFork> getCliqueForks() {
    return getForks("clique", CliqueFork::new);
  }

  private <T> List<T> getForks(
      final String fieldKey, final Function<ObjectNode, T> forkConstructor) {
    final Optional<ArrayNode> forksNode = JsonUtil.getArrayNode(customForkConfigRoot, fieldKey);

    if (forksNode.isEmpty()) {
      return emptyList();
    }

    final List<T> forks = Lists.newArrayList();

    forksNode
        .get()
        .elements()
        .forEachRemaining(
            node -> {
              if (!node.isObject()) {
                throw new IllegalArgumentException("Transition is illegally formatted.");
              }
              forks.add(forkConstructor.apply((ObjectNode) node));
            });

    return Collections.unmodifiableList(forks);
  }
}
