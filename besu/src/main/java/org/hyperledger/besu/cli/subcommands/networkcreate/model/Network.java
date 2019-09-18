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
import static java.util.Objects.isNull;

import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Generatable;
import org.hyperledger.besu.cli.subcommands.networkcreate.generate.Verifiable;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Network implements Verifiable, Generatable {

  private final String name;
  private final BigInteger chainId;
  private final PoaConsensus poaConsensus;

  private static final Logger LOG = LogManager.getLogger();
  private List<Account> accounts;

  public Network(
      @JsonProperty("name") final String name,
      @JsonProperty("chain-id") final BigInteger chainId,
      @JsonProperty("clique") final Clique clique,
      @JsonProperty("ibft2") final Ibft2 ibft2) {

    if (chainId != null) {
      this.chainId = chainId;
    } else {
      Random random = new Random();
      this.chainId = new BigInteger(100, random);
    }

    this.name = name;
    if (!isNull(clique)) {
      poaConsensus = clique;
    } else if (!isNull(ibft2)) {
      poaConsensus = ibft2;
    } else {
      poaConsensus = null;
    }
  }

  public String getName() {
    return name;
  }

  public BigInteger getChainId() {
    return chainId;
  }

  @JsonInclude(Include.NON_NULL)
  public Clique getClique() {
    return (poaConsensus instanceof Clique) ? (Clique) poaConsensus : null;
  }

  @JsonInclude(Include.NON_NULL)
  public Ibft2 getIbft2() {
    return (poaConsensus instanceof Ibft2) ? (Ibft2) poaConsensus : null;
  }

  @JsonIgnore
  public PoaConsensus getConsensus() {
    return poaConsensus;
  }

  @Override
  public InitConfigurationErrorHandler verify(final InitConfigurationErrorHandler errorHandler) {
    if (isNull(name)) errorHandler.add("Network name", "null", "Network name not defined.");
    if (isNull(poaConsensus))
      errorHandler.add(
          "Network poaConsensus (Clique or IBFT2) must be defined",
          "null",
          "Network poaConsensus (Clique or IBFT2) must be defined.");
    if (chainId.compareTo(BigInteger.ZERO) <= 0)
      errorHandler.add(
          "Network id",
          getChainId().toString(),
          "Chain ID must be a positive, greater than zero integer.");
    // TODO verify clique and accounts
    return errorHandler;
  }

  @Override
  public Path generate(final Path outputDirectoryPath) {
    final Path outputGenesisFile = outputDirectoryPath.resolve("genesis.json");
    try {
      Files.write(outputGenesisFile, buildGenesis().getBytes(UTF_8), StandardOpenOption.CREATE_NEW);
      LOG.debug("Genesis file wrote to {}", outputGenesisFile);
    } catch (IOException e) {
      LOG.error("Unable to write genesis file", e);
    }

    return outputGenesisFile;
  }

  private String buildGenesis() {
    try {
      final ObjectNode genesisTemplate = poaConsensus.getGenesisTemplate();
      ObjectNode config = (ObjectNode) genesisTemplate.get("config");

      config.put("chainId", chainId);

      String consensusKey = null;
      if (poaConsensus instanceof Clique) {
        consensusKey = "clique";
      } else if (poaConsensus instanceof Ibft2) {
        consensusKey = "ibft2";
      }
      final ObjectNode fragment = poaConsensus.getGenesisFragment();
      config.set(consensusKey, fragment);

      genesisTemplate.put("extraData", poaConsensus.getExtraData());

      ObjectMapper objectMapper = new ObjectMapper();

      if (!isNull(accounts) && !accounts.isEmpty()) {
        ObjectNode alloc = (ObjectNode) genesisTemplate.get("alloc");
        for (Account account : accounts) {
          ObjectNode accountFragment = account.getGenesisFragment();
          alloc.replace(account.getAddress().toUnprefixedString(), accountFragment);
        }
      }

      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(genesisTemplate);
    } catch (JsonProcessingException e) {
      LOG.error("Unable to generate genesis file JSON", e);
    }
    return "";
  }

  void setAccounts(List<Account> accounts) {
    this.accounts = accounts;
  }
}
