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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Test;

public class GenesisConfigFileTest {

  private static final BigInteger MAINNET_CHAIN_ID = BigInteger.ONE;
  private static final BigInteger DEVELOPMENT_CHAIN_ID = BigInteger.valueOf(2018);
  private static final GenesisConfigFile EMPTY_CONFIG = GenesisConfigFile.fromConfig("{}");

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
  public void shouldDefaultTimestampToZero() {
    assertThat(EMPTY_CONFIG.getTimestamp()).isZero();
  }

  @Test
  public void shouldGetAllocations() {
    final GenesisConfigFile config =
        GenesisConfigFile.fromConfig(
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
    final GenesisConfigFile config = GenesisConfigFile.fromConfig("{}");
    assertThat(config.streamAllocations()).isEmpty();
  }

  @Test
  public void shouldGetLargeChainId() {
    final GenesisConfigFile config =
        GenesisConfigFile.fromConfig(
            "{\"config\": { \"chainId\": 31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066470938446095 }}");
    assertThat(config.getConfigOptions().getChainId())
        .contains(
            new BigInteger(
                "31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066470938446095"));
  }

  @Test
  public void acceptComments() {
    // this test will change in the future to reject comments.
    final GenesisConfigFile config =
        GenesisConfigFile.fromConfig(
            "{\"config\": { \"chainId\": 2017 }\n/* C comment }*/\n//C++ comment }\n}");

    assertThat(config.getConfigOptions().getChainId()).contains(new BigInteger("2017"));
    // Unfortunately there is no good (non-flakey) way to assert logs.
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

    assertThat(config.getConfigOptions().getConstantinopleFixBlockNumber()).hasValue(0);
    assertThat(config.getConfigOptions().getIstanbulBlockNumber()).isNotPresent();
    assertThat(config.getConfigOptions().getChainId()).hasValue(BigInteger.valueOf(2018));
    assertThat(config.getConfigOptions().getContractSizeLimit()).hasValue(2147483647);
    assertThat(config.getConfigOptions().getEvmStackSize()).isNotPresent();
  }

  private GenesisConfigFile configWithProperty(final String key, final String value) {
    return GenesisConfigFile.fromConfig("{\"" + key + "\":\"" + value + "\"}");
  }

  private void assertInvalidConfiguration(final ThrowingCallable getter) {
    assertThatThrownBy(getter)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid genesis block configuration");
  }
}
