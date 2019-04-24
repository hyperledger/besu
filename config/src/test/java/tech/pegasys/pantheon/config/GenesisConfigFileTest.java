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
import static org.assertj.core.api.Assertions.entry;

import java.math.BigInteger;
import java.util.Map;
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
    assertThat(config.getAllocations().map(GenesisAllocation::getAddress))
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
    assertThat(config.getAllocations().map(GenesisAllocation::getAddress))
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
                + "      \"balance\": \"90000000000000000000000\""
                + "    }"
                + "  }"
                + "}");

    final Map<String, String> allocations =
        config
            .getAllocations()
            .collect(
                Collectors.toMap(GenesisAllocation::getAddress, GenesisAllocation::getBalance));
    assertThat(allocations)
        .containsOnly(
            entry("fe3b557e8fb62b89f4916b721be55ceb828dbd73", "0xad78ebc5ac6200000"),
            entry("627306090abab3a6e1400e9345bc60c78a8bef57", "1000"),
            entry("f17f52151ebef6c7334fad080c5704d77216b732", "90000000000000000000000"));
  }

  @Test
  public void shouldGetEmptyAllocationsWhenAllocNotPresent() {
    final GenesisConfigFile config = GenesisConfigFile.fromConfig("{}");
    assertThat(config.getAllocations()).isEmpty();
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

  private GenesisConfigFile configWithProperty(final String key, final String value) {
    return GenesisConfigFile.fromConfig("{\"" + key + "\":\"" + value + "\"}");
  }

  private void assertInvalidConfiguration(final ThrowingCallable getter) {
    assertThatThrownBy(getter)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid genesis block configuration");
  }
}
