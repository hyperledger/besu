/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.config;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

public class GenesisConfigOptionsTest {

  @Test
  public void shouldUseEthHashWhenEthHashInConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ethash", emptyMap()));
    assertThat(config.isEthHash()).isTrue();
    assertThat(config.getConsensusEngine()).isEqualTo("ethash");
  }

  @Test
  public void shouldNotUseEthHashIfEthHashNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isEthHash()).isFalse();
  }

  @Test
  public void shouldUseIbftLegacyWhenIbftInConfig() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("ibft", emptyMap()));
    assertThat(config.isIbftLegacy()).isTrue();
    assertThat(config.getIbftLegacyConfigOptions()).isNotSameAs(IbftConfigOptions.DEFAULT);
    assertThat(config.getConsensusEngine()).isEqualTo("ibft");
  }

  @Test
  public void shouldNotUseIbftLegacyIfIbftNotPresent() {
    final GenesisConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.isIbftLegacy()).isFalse();
    assertThat(config.getIbftLegacyConfigOptions()).isSameAs(IbftConfigOptions.DEFAULT);
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
    assertThat(config.getConstantinopleFixBlockNumber()).hasValue(1000);
  }

  @Test
  public void shouldGetIstanbulBlockNumber() {
    final GenesisConfigOptions config = fromConfigOptions(singletonMap("istanbulBlock", 1000));
    assertThat(config.getIstanbulBlockNumber()).hasValue(1000);
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
    assertThat(config.getConstantinopleFixBlockNumber()).isEmpty();
    assertThat(config.getIstanbulBlockNumber()).isEmpty();
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

  private GenesisConfigOptions fromConfigOptions(final Map<String, Object> configOptions) {
    final ObjectNode rootNode = JsonUtil.createEmptyObjectNode();
    final ObjectNode options = JsonUtil.objectNodeFromMap(configOptions);
    rootNode.set("config", options);
    return GenesisConfigFile.fromConfig(rootNode).getConfigOptions();
  }
}
