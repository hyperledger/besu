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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class GenesisConfigOptionsTest {

  @Test
  public void shouldUseEthHashWhenEthHashInConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ethash", emptyMap()));
    assertThat(config.isEthHash()).isTrue();
    assertThat(config.getConsensusEngine()).isEqualTo("ethash");
  }

  @Test
  public void shouldUseKeccak256WhenKeccak256InConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("keccak256", emptyMap()));
    assertThat(config.isKeccak256()).isTrue();
    assertThat(config.getConsensusEngine()).isEqualTo("keccak256");
  }

  @Test
  public void shouldNotUseEthHashIfEthHashNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isEthHash()).isFalse();
  }

  @Test
  public void shouldNotUseKeccak256IfEthHashNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isKeccak256()).isFalse();
  }

  @Test
  public void shouldUseIbftLegacyWhenIbftInConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ibft", emptyMap()));
    assertThat(config.isIbftLegacy()).isTrue();
    assertThat(config.getIbftLegacyConfigOptions()).isNotSameAs(JsonBftConfigOptions.DEFAULT);
    assertThat(config.getConsensusEngine()).isEqualTo("ibft");
  }

  @Test
  public void shouldNotUseIbftLegacyIfIbftNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isIbftLegacy()).isFalse();
    assertThat(config.getIbftLegacyConfigOptions()).isSameAs(IbftLegacyConfigOptions.DEFAULT);
  }

  @Test
  public void shouldUseIbft2WhenIbft2InConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ibft2", emptyMap()));
    assertThat(config.isIbftLegacy()).isFalse();
    assertThat(config.isIbft2()).isTrue();
    assertThat(config.getConsensusEngine()).isEqualTo("ibft2");
  }

  @Test
  public void shouldUseCliqueWhenCliqueInConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("clique", emptyMap()));
    assertThat(config.isClique()).isTrue();
    assertThat(config.getCliqueConfigOptions()).isNotSameAs(CliqueConfigOptions.DEFAULT);
    assertThat(config.getConsensusEngine()).isEqualTo("clique");
  }

  @Test
  public void shouldNotUseCliqueIfCliqueNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isClique()).isFalse();
    assertThat(config.getCliqueConfigOptions()).isSameAs(CliqueConfigOptions.DEFAULT);
  }

  @Test
  public void shouldGetHomesteadBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("homesteadBlock", 1000));
    assertThat(config.getHomesteadBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetDaoForkBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("daoForkBlock", 1000));
    assertThat(config.getDaoForkBlock()).hasValue(1000);
  }

  @Test
  public void shouldNotHaveDaoForkBlockWhenSetToZero() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("daoForkBlock", 0));
    assertThat(config.getDaoForkBlock()).isEmpty();
  }

  @Test
  public void shouldGetTangerineWhistleBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("eip150Block", 1000));
    assertThat(config.getTangerineWhistleBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetSpuriousDragonBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("eip158Block", 1000));
    assertThat(config.getSpuriousDragonBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetByzantiumBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("byzantiumBlock", 1000));
    assertThat(config.getByzantiumBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetConstantinopleBlockNumber() {
    final GenesisConfigOptions config =
        fromConfigOptions(singletonMap("constantinopleBlock", 1000));
    assertThat(config.getConstantinopleBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetConstantinopleFixBlockNumber() {
    final GenesisConfigOptions config =
        fromConfigOptions(singletonMap("constantinopleFixBlock", 1000));
    assertThat(config.getPetersburgBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetPetersburgBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("petersburgBlock", 1000));
    assertThat(config.getPetersburgBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldFailWithBothPetersburgAndConstantinopleFixBlockNumber() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("constantinopleFixBlock", 1000);
    configMap.put("petersburgBlock", 1000);
    final GenesisConfigOptions config = fromConfigOptions(configMap);
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(config::getPetersburgBlockNumber)
        .withMessage(
            "Genesis files cannot specify both petersburgBlock and constantinopleFixBlock.");
  }

  @Test
  public void shouldGetIstanbulBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("istanbulBlock", 1000));
    assertThat(config.getIstanbulBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetMuirGlacierBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("muirGlacierBlock", 1000));
    assertThat(config.getMuirGlacierBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetBerlinBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("berlinBlock", 1000));
    assertThat(config.getBerlinBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetLondonBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("londonblock", 1000));
    assertThat(config.getLondonBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetArrowGlacierBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("arrowGlacierBlock", 1000));
    assertThat(config.getArrowGlacierBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetGrayGlacierBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("grayGlacierBlock", 4242));
    assertThat(config.getGrayGlacierBlockNumber()).hasValue(4242);
  }

  @Test
  // TODO ECIP-1049 change for the actual fork name when known
  public void shouldGetECIP1049BlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ecip1049block", 1000));
    assertThat(config.getEcip1049BlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldNotReturnEmptyOptionalWhenBlockNumberNotSpecified() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getHomesteadBlockNumber()).isEmpty();
    assertThat(config.getDaoForkBlock()).isEmpty();
    assertThat(config.getTangerineWhistleBlockNumber()).isEmpty();
    assertThat(config.getSpuriousDragonBlockNumber()).isEmpty();
    assertThat(config.getByzantiumBlockNumber()).isEmpty();
    assertThat(config.getConstantinopleBlockNumber()).isEmpty();
    assertThat(config.getPetersburgBlockNumber()).isEmpty();
    assertThat(config.getIstanbulBlockNumber()).isEmpty();
    assertThat(config.getMuirGlacierBlockNumber()).isEmpty();
    assertThat(config.getBerlinBlockNumber()).isEmpty();
    assertThat(config.getLondonBlockNumber()).isEmpty();
    assertThat(config.getArrowGlacierBlockNumber()).isEmpty();
    assertThat(config.getGrayGlacierBlockNumber()).isEmpty();
    assertThat(config.getEcip1049BlockNumber()).isEmpty();
  }

  @Test
  public void shouldGetChainIdWhenSpecified() {
    final GenesisConfigOptions config =
        fromConfigOptions(singletonMap("chainId", BigInteger.valueOf(32)));
    assertThat(config.getChainId()).hasValue(BigInteger.valueOf(32));
  }

  @Test
  public void shouldSupportEmptyGenesisConfig() {
    final GenesisConfigOptions config = GenesisConfigFile.fromConfig("{}").getConfigOptions();
    assertThat(config.isEthHash()).isFalse();
    assertThat(config.isIbftLegacy()).isFalse();
    assertThat(config.isClique()).isFalse();
    assertThat(config.getHomesteadBlockNumber()).isEmpty();
  }

  @Test
  public void shouldGetTerminalTotalDifficultyWhenSpecified() {
    final GenesisConfigOptions config =
        fromConfigOptions(singletonMap("terminalTotalDifficulty", BigInteger.valueOf(1000)));
    assertThat(config.getTerminalTotalDifficulty()).isPresent();
    assertThat(config.getTerminalTotalDifficulty().get()).isEqualTo(UInt256.valueOf(1000));

    // stubJsonGenesis
    final GenesisConfigOptions stub =
        new StubGenesisConfigOptions().terminalTotalDifficulty(UInt256.valueOf(500));
    assertThat(stub.getTerminalTotalDifficulty()).isPresent();
    assertThat(stub.getTerminalTotalDifficulty().get()).isEqualTo(UInt256.valueOf(500));
  }

  @Test
  public void shouldNotReturnTerminalTotalDifficultyWhenNotSpecified() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getTerminalTotalDifficulty()).isNotPresent();
    // stubJsonGenesis
    assertThat(new StubGenesisConfigOptions().getTerminalTotalDifficulty()).isNotPresent();
  }

  @Test
  public void isQuorumShouldDefaultToFalse() {
    final GenesisConfigOptions config = GenesisConfigFile.fromConfig("{}").getConfigOptions();

    assertThat(config.isQuorum()).isFalse();
    assertThat(config.getQip714BlockNumber()).isEmpty();
  }

  @Test
  public void isQuorumConfigParsedCorrectly() {
    final GenesisConfigOptions config =
        fromConfigOptions(Map.of("isQuorum", true, "qip714block", 99999L));

    assertThat(config.isQuorum()).isTrue();
    assertThat(config.getQip714BlockNumber()).hasValue(99999L);
  }

  private GenesisConfigOptions fromConfigOptions(final Map<String, Object> configOptions) {
    final ObjectNode rootNode = JsonUtil.createEmptyObjectNode();
    final ObjectNode options = JsonUtil.objectNodeFromMap(configOptions);
    rootNode.set("config", options);
    return GenesisConfigFile.fromConfig(rootNode).getConfigOptions();
  }
}
