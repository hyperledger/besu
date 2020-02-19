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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

public class TransitionsConfigOptions {

  public static final String IBFT2_FORKS = "ibft2";

  public static final TransitionsConfigOptions DEFAULT =
      new TransitionsConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode customForkConfigRoot;

  @JsonCreator
  public TransitionsConfigOptions(final ObjectNode customForkConfigRoot) {
    this.customForkConfigRoot = customForkConfigRoot;
  }

  public List<IbftFork> getIbftForks() {
    final Optional<ArrayNode> ibftForksNode =
        JsonUtil.getArrayNode(customForkConfigRoot, IBFT2_FORKS);

    if (ibftForksNode.isEmpty()) {
      return emptyList();
    }

    final List<IbftFork> ibftForks = Lists.newArrayList();

    ibftForksNode
        .get()
        .elements()
        .forEachRemaining(
            node -> {
              if (!node.isObject()) {
                throw new IllegalArgumentException("Ibft2 fork is illegally formatted.");
              }
              ibftForks.add(new IbftFork((ObjectNode) node));
            });

    return Collections.unmodifiableList(ibftForks);
  }
}
