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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.config.GenesisConfigFile.fromConfig;

import org.hyperledger.besu.datatypes.Wei;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Test;

public class GenesisConfigFileTest {

  private static final BigInteger MAINNET_CHAIN_ID = BigInteger.ONE;
  private static final BigInteger DEVELOPMENT_CHAIN_ID = BigInteger.valueOf(1337);
  private static final GenesisConfigFile EMPTY_CONFIG = fromConfig("{}");

  @Test
  public void shouldLoadMainnetConfigFile() {
    final GenesisConfigFile config = GenesisConfigFile.mainnet();
    // Sanity check some basic properties to confirm this is the mainnet file.
    assertThat(config.getConfigOptions().isEthHash()).isTrue();
    assertThat(config.getConfigOptions().getChainId()).hasValue(MAINNET_CHAIN_ID);
    assertThat(config.streamAllocations().map(GenesisAllocation::getAddress))
        .contains(
            "000d836201318ec6899a67540690382780743280",
            "001762430ea9c3a26e5749afdb70da5f78ddbb8c",
            "001d14804b399c6ef80e64576f657660804fec0b");
  }

  @Test
  public void shouldLoadDevelopmentConfigFile() {
    final GenesisConfigFile config = GenesisConfigFile.development();
    // Sanity check some basic properties to confirm this is the dev file.
    assertThat(config.getConfigOptions().isEthHash()).isTrue();
    assertThat(config.getConfigOptions().getChainId()).hasValue(DEVELOPMENT_CHAIN_ID);
    assertThat(config.streamAllocations().map(GenesisAllocation::getAddress))
        .contains(
            "fe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "627306090abab3a6e1400e9345bc60c78a8bef57",
            "f17f52151ebef6c7334fad080c5704d77216b732");
  }

  @Test
  public void shouldGetParentHash() {
    assertThat(configWithProperty("parentHash", "844633").getParentHash()).isEqualTo("844633");
  }

  @Test
  public void shouldDefaultParentHashToEmptyString() {
    assertThat(EMPTY_CONFIG.getParentHash()).isEmpty();
  }

  @Test
  public void shouldGetDifficulty() {
    assertThat(configWithProperty("difficulty", "1234").getDifficulty()).isEqualTo("1234");
  }

  @Test
  public void shouldRequireDifficulty() {
    assertInvalidConfiguration(EMPTY_CONFIG::getDifficulty);
  }

  @Test
  public void shouldGetExtraData() {
    assertThat(configWithProperty("extraData", "yay").getExtraData()).isEqualTo("yay");
  }

  @Test
  public void shouldDefaultExtraDataToEmptyString() {
    assertThat(EMPTY_CONFIG.getExtraData()).isEmpty();
  }

  @Test
  public void shouldGetGasLimit() {
    assertThat(configWithProperty("gasLimit", "1000").getGasLimit()).isEqualTo(1000);
  }

  @Test
  public void shouldRequireGasLimit() {
    assertInvalidConfiguration(EMPTY_CONFIG::getGasLimit);
  }

  @Test
  public void shouldGetMixHash() {
    assertThat(configWithProperty("mixHash", "asdf").getMixHash()).isEqualTo("asdf");
  }

  @Test
  public void shouldDefaultMixHashToEmptyString() {
    assertThat(EMPTY_CONFIG.getMixHash()).isEmpty();
  }

  @Test
  public void shouldGetNonce() {
    assertThat(configWithProperty("nonce", "0x10").getNonce()).isEqualTo("0x10");
  }

  @Test
  public void shouldDefaultNonceToZero() {
    assertThat(EMPTY_CONFIG.getNonce()).isEqualTo("0x0");
  }

  @Test
  public void shouldGetCoinbase() {
    assertThat(configWithProperty("coinbase", "abcd").getCoinbase()).contains("abcd");
  }

  @Test
  public void shouldReturnEmptyWhenCoinbaseNotSpecified() {
    assertThat(EMPTY_CONFIG.getCoinbase()).isEmpty();
  }

  @Test
  public void shouldGetTimestamp() {
    assertThat(configWithProperty("timestamp", "0x10").getTimestamp()).isEqualTo(16L);
  }

  @Test
  public void shouldGetBaseFeeAtGenesis() {
    GenesisConfigFile withBaseFeeAtGenesis =
        GenesisConfigFile.fromConfig("{\"config\":{\"londonBlock\":0},\"baseFeePerGas\":\"0xa\"}");
    assertThat(withBaseFeeAtGenesis.getBaseFeePerGas()).isPresent();
    assertThat(withBaseFeeAtGenesis.getBaseFeePerGas().get().toLong()).isEqualTo(10L);
  }

  @Test
  public void shouldGetDefaultBaseFeeAtGenesis() {
    GenesisConfigFile withBaseFeeAtGenesis =
        GenesisConfigFile.fromConfig("{\"config\":{\"londonBlock\":0}}");
    // no specified baseFeePerGas:
    assertThat(withBaseFeeAtGenesis.getBaseFeePerGas()).isNotPresent();
    // supply a default genesis baseFeePerGas when london-at-genesis:
    assertThat(withBaseFeeAtGenesis.getGenesisBaseFeePerGas().get())
        .isEqualTo(GenesisConfigFile.BASEFEE_AT_GENESIS_DEFAULT_VALUE);
  }

  @Test
  public void shouldNotGetBaseFeeAtGenesis() {
    GenesisConfigFile withBaseFeeNotAtGenesis =
        GenesisConfigFile.fromConfig("{\"config\":{\"londonBlock\":10},\"baseFeePerGas\":\"0xa\"}");
    // specified baseFeePerGas:
    assertThat(withBaseFeeNotAtGenesis.getBaseFeePerGas().get().toLong()).isEqualTo(10L);
    // but no baseFeePerGas since london block is not at genesis:
    assertThat(withBaseFeeNotAtGenesis.getGenesisBaseFeePerGas()).isNotPresent();
  }

  @Test
  public void shouldOverrideConfigOptionsBaseFeeWhenSpecified() {
    GenesisConfigOptions withOverrides =
        EMPTY_CONFIG.getConfigOptions(Map.of("baseFeePerGas", Wei.of(8).toString()));
    assertThat(withOverrides.getBaseFeePerGas().get().toLong()).isEqualTo(8L);
  }

  @Test
  public void shouldGetTerminalTotalDifficultyAtGenesis() {
    GenesisConfigFile withTerminalTotalDifficultyAtGenesis =
        fromConfig("{\"config\":{\"terminalTotalDifficulty\":1000}}");
    assertThat(
            withTerminalTotalDifficultyAtGenesis
                .getConfigOptions()
                .getTerminalTotalDifficulty()
                .get())
        .isEqualTo(UInt256.valueOf(1000L));
  }

  @Test
  public void shouldGetEmptyTerminalTotalDifficultyAtGenesis() {
    assertThat(EMPTY_CONFIG.getConfigOptions().getTerminalTotalDifficulty()).isNotPresent();
  }

  @Test
  public void assertRopstenTerminalTotalDifficulty() {
    GenesisConfigOptions ropstenOptions =
        GenesisConfigFile.genesisFileFromResources("/ropsten.json").getConfigOptions();

    assertThat(ropstenOptions.getTerminalTotalDifficulty()).isPresent();
    assertThat(ropstenOptions.getTerminalTotalDifficulty().get())
        .isEqualTo(UInt256.valueOf(new BigInteger("100000000000000000000000")));
  }

  @Test
  public void assertTerminalTotalDifficultyOverride() {
    GenesisConfigOptions ropstenOverrideOptions =
        GenesisConfigFile.genesisFileFromResources("/ropsten.json")
            .getConfigOptions(Map.of("terminalTotalDifficulty", String.valueOf(Long.MAX_VALUE)));

    assertThat(ropstenOverrideOptions.getTerminalTotalDifficulty()).isPresent();
    assertThat(ropstenOverrideOptions.getTerminalTotalDifficulty().get())
        .isEqualTo(UInt256.valueOf(Long.MAX_VALUE));
  }

  @Test
  public void shouldFindParisForkAndAlias() {
    GenesisConfigFile parisGenesis =
        GenesisConfigFile.fromConfig("{\"config\":{\"parisBlock\":10},\"baseFeePerGas\":\"0xa\"}");
    assertThat(parisGenesis.getForks()).hasSize(1);
    assertThat(parisGenesis.getConfigOptions().getParisBlockNumber()).isPresent();
    assertThat(parisGenesis.getConfigOptions().getParisBlockNumber().getAsLong()).isEqualTo(10L);

    GenesisConfigFile premergeForkGenesis =
        GenesisConfigFile.fromConfig(
            "{\"config\":{\"preMergeForkBlock\":11},\"baseFeePerGas\":\"0xa\"}");
    assertThat(premergeForkGenesis.getForks()).hasSize(1);
    assertThat(premergeForkGenesis.getConfigOptions().getParisBlockNumber()).isPresent();
    assertThat(premergeForkGenesis.getConfigOptions().getParisBlockNumber().getAsLong())
        .isEqualTo(11L);

    // assert fail if both paris and alias are present
    var dupeOptions =
        GenesisConfigFile.fromConfig(
                "{\"config\":{\"parisBlock\":10,\"preMergeForkBlock\":11},\"baseFeePerGas\":\"0xa\"}")
            .getConfigOptions();
    assertThatThrownBy(() -> dupeOptions.getParisBlockNumber())
        .isInstanceOf(RuntimeException.class);

    // assert empty if neither are present:
    GenesisConfigFile londonGenesis =
        GenesisConfigFile.fromConfig("{\"config\":{\"londonBlock\":11},\"baseFeePerGas\":\"0xa\"}");
    assertThat(londonGenesis.getForks()).hasSize(1);
    assertThat(londonGenesis.getConfigOptions().getParisBlockNumber()).isEmpty();
  }

  @Test
  public void shouldDefaultTimestampToZero() {
    assertThat(EMPTY_CONFIG.getTimestamp()).isZero();
  }

  @Test
  public void shouldGetAllocations() {
    final GenesisConfigFile config =
        fromConfig(
            "{"
                + "  \"alloc\": {"
                + "    \"fe3b557e8fb62b89f4916b721be55ceb828dbd73\": {"
                + "      \"balance\": \"0xad78ebc5ac6200000\""
                + "    },"
                + "    \"627306090abaB3A6e1400e9345bC60c78a8BEf57\": {"
                + "      \"balance\": \"1000\""
                + "    },"
                + "    \"f17f52151EbEF6C7334FAD080c5704D77216b732\": {"
                + "      \"balance\": \"90000000000000000000000\","
                + "        \"storage\": {"
                + "          \"0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a4\": \"0x937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0\",\n"
                + "          \"0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a3\": \"0x6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012\""
                + "        }"
                + "    }"
                + "  }"
                + "}");

    final Map<String, GenesisAllocation> allocations =
        config
            .streamAllocations()
            .collect(Collectors.toMap(GenesisAllocation::getAddress, Function.identity()));
    assertThat(allocations.keySet())
        .containsOnly(
            "fe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "627306090abab3a6e1400e9345bc60c78a8bef57",
            "f17f52151ebef6c7334fad080c5704d77216b732");
    final GenesisAllocation alloc1 = allocations.get("fe3b557e8fb62b89f4916b721be55ceb828dbd73");
    final GenesisAllocation alloc2 = allocations.get("627306090abab3a6e1400e9345bc60c78a8bef57");
    final GenesisAllocation alloc3 = allocations.get("f17f52151ebef6c7334fad080c5704d77216b732");

    assertThat(alloc1.getBalance()).isEqualTo("0xad78ebc5ac6200000");
    assertThat(alloc2.getBalance()).isEqualTo("1000");
    assertThat(alloc3.getBalance()).isEqualTo("90000000000000000000000");
    assertThat(alloc3.getStorage().size()).isEqualTo(2);
    assertThat(
            alloc3
                .getStorage()
                .get("0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a4"))
        .isEqualTo("0x937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0");
    assertThat(
            alloc3
                .getStorage()
                .get("0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a3"))
        .isEqualTo("0x6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012");
  }

  @Test
  public void shouldGetEmptyAllocationsWhenAllocNotPresent() {
    final GenesisConfigFile config = fromConfig("{}");
    assertThat(config.streamAllocations()).isEmpty();
  }

  @Test
  public void shouldGetLargeChainId() {
    final GenesisConfigFile config =
        fromConfig(
            "{\"config\": { \"chainId\": 31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066470938446095 }}");
    assertThat(config.getConfigOptions().getChainId())
        .contains(
            new BigInteger(
                "31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066470938446095"));
  }

  @Test
  public void mustNotAcceptComments() {
    assertThatThrownBy(
            () ->
                fromConfig(
                    "{\"config\": { \"chainId\": 2017 }\n/* C comment }*/\n//C++ comment }\n}"))
        .hasCauseInstanceOf(JsonParseException.class)
        .hasMessageContaining("Unexpected character ('/'");
  }

  @Test
  public void testOverridePresent() {
    final GenesisConfigFile config = GenesisConfigFile.development();
    final int bigBlock = 999_999_999;
    final String bigBlockString = Integer.toString(bigBlock);
    final Map<String, String> override = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    override.put("istanbulBlock", bigBlockString);
    override.put("chainId", bigBlockString);
    override.put("contractSizeLimit", bigBlockString);

    assertThat(config.getForks()).isNotEmpty();
    assertThat(config.getConfigOptions(override).getIstanbulBlockNumber()).hasValue(bigBlock);
    assertThat(config.getConfigOptions(override).getChainId())
        .hasValue(BigInteger.valueOf(bigBlock));
    assertThat(config.getConfigOptions(override).getContractSizeLimit()).hasValue(bigBlock);
  }

  @Test
  public void testOverrideNull() {
    final GenesisConfigFile config = GenesisConfigFile.development();
    final Map<String, String> override = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    override.put("istanbulBlock", null);
    override.put("chainId", null);
    override.put("contractSizeLimit", null);

    assertThat(config.getForks()).isNotEmpty();
    assertThat(config.getConfigOptions(override).getIstanbulBlockNumber()).isNotPresent();
    assertThat(config.getConfigOptions(override).getChainId()).isNotPresent();
    assertThat(config.getConfigOptions(override).getContractSizeLimit()).isNotPresent();
  }

  @Test
  public void testOverrideCaseInsensitivity() {
    final GenesisConfigFile config = GenesisConfigFile.development();
    final int bigBlock = 999_999_999;
    final String bigBlockString = Integer.toString(bigBlock);
    final Map<String, String> override = new HashMap<>();
    // as speicified
    override.put("istanbulBlock", bigBlockString);
    // ALL CAPS
    override.put("CHAINID", bigBlockString);
    // all lower case
    override.put("contractsizelimit", bigBlockString);

    assertThat(config.getConfigOptions(override).getIstanbulBlockNumber()).hasValue(bigBlock);
    assertThat(config.getConfigOptions(override).getChainId())
        .hasValue(BigInteger.valueOf(bigBlock));
    assertThat(config.getConfigOptions(override).getContractSizeLimit()).hasValue(bigBlock);
  }

  @Test
  public void testOverrideEmptyString() {
    final GenesisConfigFile config = GenesisConfigFile.development();
    final Map<String, String> override = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    override.put("istanbulBlock", "");
    override.put("chainId", "");
    override.put("contractSizeLimit", "");

    assertThat(config.getConfigOptions(override).getIstanbulBlockNumber()).isNotPresent();
    assertThat(config.getConfigOptions(override).getChainId()).isNotPresent();
    assertThat(config.getConfigOptions(override).getContractSizeLimit()).isNotPresent();
  }

  @Test
  public void testNoOverride() {
    final GenesisConfigFile config = GenesisConfigFile.development();

    assertThat(config.getConfigOptions().getPetersburgBlockNumber()).hasValue(0);
    assertThat(config.getConfigOptions().getIstanbulBlockNumber()).isNotPresent();
    assertThat(config.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(1337));
    assertThat(config.getConfigOptions().getContractSizeLimit()).hasValue(2147483647);
    assertThat(config.getConfigOptions().getEvmStackSize()).isNotPresent();
    assertThat(config.getConfigOptions().getEcip1017EraRounds()).isNotPresent();
  }

  @Test
  public void testConstantinopleFixShouldNotBeSupportedAlongPetersburg() {
    // petersburg node
    final GenesisConfigFile config = GenesisConfigFile.development();

    assertThat(config.getConfigOptions().getPetersburgBlockNumber()).hasValue(0);

    // constantinopleFix node
    final Map<String, String> override = new HashMap<>();
    override.put("constantinopleFixBlock", "1000");

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> config.getConfigOptions(override).getPetersburgBlockNumber())
        .withMessage(
            "Genesis files cannot specify both petersburgBlock and constantinopleFixBlock.");
  }

  @Test
  public void shouldLoadForksInSortedOrder() throws IOException {
    final ObjectNode configNode =
        new ObjectMapper()
            .createObjectNode()
            .set(
                "config",
                JsonUtil.objectNodeFromString(
                    Resources.toString(
                        Resources.getResource(
                            // If you inspect this config, you should see that fork block 2 is
                            // declared before 1
                            "valid_config_with_custom_forks.json"),
                        StandardCharsets.UTF_8)));

    final GenesisConfigFile config = fromConfig(configNode);

    assertThat(config.getForks()).containsExactly(1L, 2L, 3L, 1035301L);
    assertThat(config.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(4));
  }

  @Test
  public void shouldLoadForksIgnoreClassicForkBlock() throws IOException {
    final ObjectNode configNode =
        new ObjectMapper()
            .createObjectNode()
            .set(
                "config",
                JsonUtil.objectNodeFromString(
                    Resources.toString(
                        Resources.getResource(
                            // If you inspect this config, you should see that classicForkBlock is
                            // declared (which we want to ignore)
                            "valid_config_with_etc_forks.json"),
                        StandardCharsets.UTF_8)));
    final GenesisConfigFile config = fromConfig(configNode);

    assertThat(config.getForks()).containsExactly(1L, 2L, 3L, 1035301L);
    assertThat(config.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(61));
  }

  @Test
  public void shouldLoadForksIgnoreUnexpectedValues() throws IOException {
    final ObjectNode configNoUnexpectedForks =
        new ObjectMapper()
            .createObjectNode()
            .set(
                "config",
                JsonUtil.objectNodeFromString(
                    Resources.toString(
                        Resources.getResource("valid_config.json"), StandardCharsets.UTF_8)));

    final ObjectNode configClassicFork =
        new ObjectMapper()
            .createObjectNode()
            .set(
                "config",
                JsonUtil.objectNodeFromString(
                    Resources.toString(
                        Resources.getResource(
                            // If you inspect this config, you should see that classicForkBlock is
                            // declared (which we want to ignore)
                            "valid_config_with_etc_forks.json"),
                        StandardCharsets.UTF_8)));

    final ObjectNode configMultipleUnexpectedForks =
        new ObjectMapper()
            .createObjectNode()
            .set(
                "config",
                JsonUtil.objectNodeFromString(
                    Resources.toString(
                        Resources.getResource(
                            // If you inspect this config, you should see that
                            // 'unexpectedFork1Block',
                            // 'unexpectedFork2Block' and 'unexpectedFork3Block' are
                            // declared (which we want to ignore)
                            "valid_config_with_unexpected_forks.json"),
                        StandardCharsets.UTF_8)));

    final GenesisConfigFile configFileNoUnexpectedForks = fromConfig(configNoUnexpectedForks);
    final GenesisConfigFile configFileClassicFork = fromConfig(configClassicFork);
    final GenesisConfigFile configFileMultipleUnexpectedForks =
        fromConfig(configMultipleUnexpectedForks);

    assertThat(configFileNoUnexpectedForks.getForks()).containsExactly(1L, 2L, 3L, 1035301L);
    assertThat(configFileNoUnexpectedForks.getForks()).isEqualTo(configFileClassicFork.getForks());
    assertThat(configFileNoUnexpectedForks.getForks())
        .isEqualTo(configFileMultipleUnexpectedForks.getForks());
    assertThat(configFileNoUnexpectedForks.getConfigOptions().getChainId())
        .hasValue(BigInteger.valueOf(61));
  }

  /**
   * The intent of this test is to catch encoding errors when a new hard fork is being added and the
   * config is being inserted in all the places the prior fork was. The intent is that
   * all_forks.json will also be updated.
   *
   * <p>This catches a common error in JsonGenesisConfigOptions where internally the names are all
   * lower ase but 'canonicaly' they are mixed case, as well as being mixed case almost everywhere
   * else in the code. Case differences are common in custom genesis files so historically we have
   * been case agnostic.
   */
  @Test
  public void roundTripForkIdBlocks() throws IOException {
    final String configText =
        Resources.toString(Resources.getResource("all_forks.json"), StandardCharsets.UTF_8);
    final ObjectNode genesisNode = JsonUtil.objectNodeFromString(configText);

    final GenesisConfigFile genesisConfig = fromConfig(genesisNode);

    final ObjectNode output = JsonUtil.objectNodeFromMap(genesisConfig.getConfigOptions().asMap());

    assertThat(JsonUtil.getJson(output, true))
        .isEqualTo(JsonUtil.getJson(genesisNode.get("config"), true));
  }

  private GenesisConfigFile configWithProperty(final String key, final String value) {
    return fromConfig("{\"" + key + "\":\"" + value + "\"}");
  }

  private void assertInvalidConfiguration(final ThrowingCallable getter) {
    assertThatThrownBy(getter)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid genesis block configuration");
  }
}
