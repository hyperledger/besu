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
package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;

public class GenesisConfigurationFactory {

  public Optional<String> createCliqueGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = readGenesisFile("/clique/clique.json");
    return updateGenesisExtraData(
        validators, template, CliqueExtraData::createGenesisExtraDataString);
  }

  public Optional<String> createIbft2GenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    return createIbft2GenesisConfig(validators, "/ibft/ibft.json");
  }

  public Optional<String> createIbft2GenesisConfig(
      final Collection<? extends RunnableNode> validators, final String genesisFile) {
    final String template = readGenesisFile(genesisFile);
    return updateGenesisExtraData(
        validators, template, IbftExtraDataCodec::createGenesisExtraDataString);
  }

  public Optional<String> createIbft2GenesisConfigFilterBootnode(
      final Collection<? extends RunnableNode> validators, final String genesisFile) {
    final String template = readGenesisFile(genesisFile);
    final List<? extends RunnableNode> filteredList =
        validators.stream()
            .filter(node -> !node.getConfiguration().isBootnodeEligible())
            .collect(toList());
    return updateGenesisExtraData(
        filteredList, template, IbftExtraDataCodec::createGenesisExtraDataString);
  }

  public Optional<String> createPrivacyIbft2GenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = readGenesisFile("/ibft/privacy-ibft.json");
    return updateGenesisExtraData(
        validators, template, IbftExtraDataCodec::createGenesisExtraDataString);
  }

  public Optional<String> createQbftGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = readGenesisFile("/qbft/qbft.json");
    return updateGenesisExtraData(
        validators, template, QbftExtraDataCodec::createGenesisExtraDataString);
  }

  public Optional<String> createQbftContractBasedValidatorGenesisConfig(
      final Collection<? extends RunnableNode> validators) {
    final String template = readGenesisFile("/qbft/qbft-validator-contract.json");
    return updateQbftValidatorContractGenesis(validators, template);
  }

  private Optional<String> updateGenesisExtraData(
      final Collection<? extends RunnableNode> validators,
      final String genesisTemplate,
      final Function<List<Address>, String> extraDataCreator) {
    final List<Address> addresses =
        validators.stream().map(RunnableNode::getAddress).collect(toList());
    final String extraDataString = extraDataCreator.apply(addresses);
    final String genesis = genesisTemplate.replaceAll("%extraData%", extraDataString);
    return Optional.of(genesis);
  }

  private Optional<String> updateQbftValidatorContractGenesis(
      final Collection<? extends RunnableNode> validators, final String genesisTemplate) {
    final List<String> addresses =
        validators.stream()
            .map(RunnableNode::getAddress)
            .map(Bytes::toUnprefixedHexString)
            .map(hex -> (String.format("%064x", 0) + hex).substring(hex.length()))
            .collect(toList());

    final Map<String, String> storageValues = new LinkedHashMap<>();
    storageValues.put(String.format("%064x", 0), String.format("%064x", addresses.size()));

    // this magical location is start of the values in the array in the sample contract our genesis
    // file is using.
    final BigInteger location =
        new BigInteger("290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563", 16);
    for (int i = 0; i < addresses.size(); i++) {
      final BigInteger varStorage = location.add(BigInteger.valueOf(i));
      storageValues.put(varStorage.toString(16), addresses.get(i));
    }

    try {
      final String jsonValue = new ObjectMapper().writeValueAsString(storageValues);
      final String genesis = genesisTemplate.replaceAll("%storage%", jsonValue);
      return Optional.of(genesis);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  public String readGenesisFile(final String filepath) {
    try {
      final URI uri = this.getClass().getResource(filepath).toURI();
      return Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (final URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to get test genesis config " + filepath);
    }
  }
}
