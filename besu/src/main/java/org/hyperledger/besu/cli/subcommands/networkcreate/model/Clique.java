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
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;

import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;

// TODO Handle errors
class Clique implements PoaConsensus {

  private static final String GENESIS_TEMPLATE = "clique-genesis-template.json";

  private Integer blockPeriodSeconds;
  private Integer epochLength;
  private List<Node> validators;
  private ConfigNode parent;

  public Clique(
      @JsonProperty("block-period-seconds") final Integer blockPeriodSeconds,
      @JsonProperty("epoch-length") final Integer epochLength) {

    this.blockPeriodSeconds = blockPeriodSeconds;
    this.epochLength = epochLength;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Integer getBlockPeriodSeconds() {
    return blockPeriodSeconds;
  }

  @SuppressWarnings("unused") // Used by Jackson serialisation
  public Integer getEpochLength() {
    return epochLength;
  }

  @JsonIgnore
  @Override
  public ObjectNode getGenesisFragment() {
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode fragment = mapper.createObjectNode();
    fragment.set("blockperiodseconds", mapper.convertValue(blockPeriodSeconds, JsonNode.class));
    fragment.set("epochlength", mapper.convertValue(epochLength, JsonNode.class));
    return fragment;
  }

  @Override
  public String getExtraData() {
    final String extraData =
        CliqueExtraData.createWithoutProposerSeal(
                BytesValue.wrap(new byte[32]),
                validators.stream().map(Node::getAddress).collect(Collectors.toList()))
            .toString();
    return extraData;
  }

  @Override
  public void setValidators(final List<Node> validators) {
    this.validators = validators;
  }

  @Override
  public ObjectNode getGenesisTemplate() {
    try {
      final URL genesisTemplateFile = getClass().getClassLoader().getResource(GENESIS_TEMPLATE);
      checkNotNull(genesisTemplateFile, "Genesis template not found.");
      final String genesisTemplateSource = Resources.toString(genesisTemplateFile, UTF_8);
      return JsonUtil.objectNodeFromString(genesisTemplateSource);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to get genesis template " + GENESIS_TEMPLATE);
    }
  }

  @Override
  public void setParent(final ConfigNode parent) {
    this.parent = parent;
  }

  @Override
  public ConfigNode getParent() {
    return parent;
  }

  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(blockPeriodSeconds)) {
      errorHandler.add("Clique block-period-seconds", "null", "block-period-seconds not defined.");
    }
    if (!isNull(blockPeriodSeconds) && blockPeriodSeconds <= 0) {
      errorHandler.add(
          "Clique block-period-seconds",
          blockPeriodSeconds.toString(),
          "block-period-seconds must be greater than zero.");
    }

    if (isNull(epochLength)) {
      errorHandler.add("Clique epoch-length", "null", "epoch-length not defined.");
    }

    return errorHandler;
  }
}
