/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.subcommands.networkcreate.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

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
import org.checkerframework.checker.nullness.qual.NonNull;

// TODO Handle errors
class Clique implements PoaConsensus {

  private static final String GENESIS_TEMPLATE = "/networkcreate/clique-genesis-template.json";

  private Integer blockPeriodSeconds;
  private Integer epochLength;
  private List<Node> validators;

  public Clique(
      @NonNull @JsonProperty("block-period-seconds") final Integer blockPeriodSeconds,
      @NonNull @JsonProperty("epoch-length") final Integer epochLength) {

    this.blockPeriodSeconds =
        requireNonNull(blockPeriodSeconds, "CLique block-period-seconds not defined.");
    this.epochLength = requireNonNull(epochLength, "Clique epoch-length not defined.");
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
      final URL genesisTemplateFile = this.getClass().getResource(GENESIS_TEMPLATE);
      final String genesisTemplateSource = Resources.toString(genesisTemplateFile, UTF_8);
      return JsonUtil.objectNodeFromString(genesisTemplateSource);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to get genesis template " + GENESIS_TEMPLATE);
    }
  }
}
