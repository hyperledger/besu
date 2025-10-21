/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenesisConfigServiceTest {

  @TempDir File tempFolder;

  @Test
  void shouldLoadBesuFormatGenesisFile() throws IOException {
    // Besu format has ethash field
    final String besuGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1,"
            + "    \"londonBlock\": 0,"
            + "    \"ethash\": {}"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(besuGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    assertThat(service.getGenesisConfig()).isNotNull();
    assertThat(service.getGenesisConfigOptions().isEthHash()).isTrue();
    assertThat(service.getGenesisConfigOptions().getChainId())
        .hasValue(java.math.BigInteger.valueOf(1));
  }

  @Test
  void shouldDetectAndTransformGethFormatWithMergeNetsplitBlock() throws IOException {
    // Geth format has mergeNetsplitBlock but no ethash
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 0,"
            + "    \"mergeNetsplitBlock\": 100"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should have ethash field added
    assertThat(service.getGenesisConfigOptions().isEthHash()).isTrue();

    // Should have preMergeForkBlock mapped from mergeNetsplitBlock
    assertThat(service.getGenesisConfigOptions().getMergeNetSplitBlockNumber()).hasValue(100L);

    // Should have withdrawal and consolidation request contract addresses added
    assertThat(service.getGenesisConfigOptions().getWithdrawalRequestContractAddress())
        .hasValue(
            org.hyperledger.besu.datatypes.Address.fromHexString(
                "0x00000961ef480eb55e80d19ad83579a64c007002"));
    assertThat(service.getGenesisConfigOptions().getConsolidationRequestContractAddress())
        .hasValue(
            org.hyperledger.besu.datatypes.Address.fromHexString(
                "0x0000bbddc7ce488642fb579f8b00f3a590007251"));
  }

  @Test
  void shouldAddBaseFeeWhenLondonAtGenesis() throws IOException {
    // Geth format without baseFeePerGas, but London at genesis
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 0,"
            + "    \"mergeNetsplitBlock\": 100"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should have baseFeePerGas added with default value (1 gwei = 0x3B9ACA00)
    assertThat(service.getGenesisConfig().getGenesisBaseFeePerGas())
        .hasValue(GenesisConfig.BASEFEE_AT_GENESIS_DEFAULT_VALUE);
  }

  @Test
  void shouldNotAddBaseFeeWhenLondonNotAtGenesis() throws IOException {
    // Geth format without baseFeePerGas, London not at genesis
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 100,"
            + "    \"mergeNetsplitBlock\": 200"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should NOT add baseFeePerGas when London is not at genesis
    assertThat(service.getGenesisConfig().getBaseFeePerGas()).isNotPresent();
  }

  @Test
  void shouldPreserveExistingBaseFee() throws IOException {
    // Geth format with explicit baseFeePerGas
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 0,"
            + "    \"mergeNetsplitBlock\": 100"
            + "  },"
            + "  \"baseFeePerGas\": \"0x2710\","
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should preserve existing baseFeePerGas (0x2710 = 10000 wei)
    assertThat(service.getGenesisConfig().getBaseFeePerGas())
        .hasValue(org.hyperledger.besu.datatypes.Wei.of(10000L));
  }

  @Test
  void shouldNotTransformBesuFormat() throws IOException {
    // Besu format already has ethash, no mergeNetsplitBlock
    final String besuGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1,"
            + "    \"londonBlock\": 0,"
            + "    \"ethash\": {}"
            + "  },"
            + "  \"baseFeePerGas\": \"0x3B9ACA00\","
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(besuGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should load without transformation - already in Besu format
    assertThat(service.getGenesisConfigOptions().isEthHash()).isTrue();
    assertThat(service.getGenesisConfig().getGenesisBaseFeePerGas())
        .hasValue(GenesisConfig.BASEFEE_AT_GENESIS_DEFAULT_VALUE);
  }

  @Test
  void shouldApplyOverrides() throws IOException {
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 0,"
            + "    \"mergeNetsplitBlock\": 100"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, java.util.Map.of("chainId", "9999"));

    // Override should be applied
    assertThat(service.getGenesisConfigOptions().getChainId())
        .hasValue(java.math.BigInteger.valueOf(9999));
  }

  @Test
  void shouldLoadFromResource() {
    final GenesisConfigService service =
        GenesisConfigService.fromResource("/dev.json", Collections.emptyMap());

    assertThat(service.getGenesisConfig()).isNotNull();
    assertThat(service.getGenesisConfigOptions().isEthHash()).isTrue();
  }

  @Test
  void shouldLoadFromGenesisConfig() {
    final GenesisConfig config = GenesisConfig.fromResource("/dev.json");
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getGenesisConfig()).isEqualTo(config);
    assertThat(service.getGenesisConfigOptions()).isNotNull();
  }

  @Test
  void shouldGetEcCurve() {
    final GenesisConfig config =
        GenesisConfig.fromConfig("{\"config\":{\"ecCurve\":\"secp256r1\"}}");
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getEcCurve()).hasValue("secp256r1");
  }

  @Test
  void shouldReturnEmptyEcCurveWhenNotSpecified() {
    final GenesisConfig config = GenesisConfig.fromConfig("{\"config\":{}}");
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getEcCurve()).isEmpty();
  }

  @Test
  void shouldGetBlockPeriodForClique() {
    final String cliqueGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"clique\": {"
            + "      \"blockperiodseconds\": 5,"
            + "      \"epochlength\": 30000"
            + "    }"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(cliqueGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getBlockPeriodSeconds()).hasValue(5);
  }

  @Test
  void shouldGetBlockPeriodForQbft() {
    final String qbftGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"qbft\": {"
            + "      \"blockperiodseconds\": 2,"
            + "      \"epochlength\": 30000"
            + "    }"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(qbftGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getBlockPeriodSeconds()).hasValue(2);
  }

  @Test
  void shouldReturnEmptyBlockPeriodForEthash() {
    final String ethashGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1,"
            + "    \"ethash\": {}"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(ethashGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.getBlockPeriodSeconds()).isEmpty();
  }

  @Test
  void shouldHandleInvalidGenesisFile() {
    final File nonExistentFile = new File(tempFolder, "nonexistent.json");

    assertThatThrownBy(() -> GenesisConfigService.fromFile(nonExistentFile, Collections.emptyMap()))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(java.io.FileNotFoundException.class);
  }

  @Test
  void shouldDetectKzgForkForCancun() {
    final String cancunGenesis =
        "{\"config\":{\"chainId\":1,\"cancunTime\":1710338135},\"difficulty\":\"0x1\",\"gasLimit\":\"0x1000000\"}";

    final GenesisConfig config = GenesisConfig.fromConfig(cancunGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.hasKzgFork()).isTrue();
  }

  @Test
  void shouldDetectKzgForkForPrague() {
    final String pragueGenesis =
        "{\"config\":{\"chainId\":1,\"pragueTime\":1720000000},\"difficulty\":\"0x1\",\"gasLimit\":\"0x1000000\"}";

    final GenesisConfig config = GenesisConfig.fromConfig(pragueGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.hasKzgFork()).isTrue();
  }

  @Test
  void shouldDetectNoKzgForkForPreCancun() {
    final String shanghaiGenesis =
        "{\"config\":{\"chainId\":1,\"shanghaiTime\":1681338455},\"difficulty\":\"0x1\",\"gasLimit\":\"0x1000000\"}";

    final GenesisConfig config = GenesisConfig.fromConfig(shanghaiGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.hasKzgFork()).isFalse();
  }

  @Test
  void shouldDetectNoKzgForkWhenNoForksSpecified() {
    final String basicGenesis =
        "{\"config\":{\"chainId\":1},\"difficulty\":\"0x1\",\"gasLimit\":\"0x1000000\"}";

    final GenesisConfig config = GenesisConfig.fromConfig(basicGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.hasKzgFork()).isFalse();
  }

  @Test
  void shouldDetectPoaConsensusForClique() {
    final String cliqueGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"clique\": {"
            + "      \"blockperiodseconds\": 5,"
            + "      \"epochlength\": 30000"
            + "    }"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(cliqueGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.isPoaConsensus()).isTrue();
    assertThat(service.getPoaEpochLength()).hasValue(30000L);
    assertThat(service.getConsensusMechanism()).isEqualTo("Clique");
  }

  @Test
  void shouldDetectPoaConsensusForQbft() {
    final String qbftGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"qbft\": {"
            + "      \"blockperiodseconds\": 2,"
            + "      \"epochlength\": 50000"
            + "    }"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(qbftGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.isPoaConsensus()).isTrue();
    assertThat(service.getPoaEpochLength()).hasValue(50000L);
    assertThat(service.getConsensusMechanism()).isEqualTo("QBFT");
  }

  @Test
  void shouldNotDetectPoaConsensusForEthash() {
    final String ethashGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1,"
            + "    \"ethash\": {}"
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final GenesisConfig config = GenesisConfig.fromConfig(ethashGenesis);
    final GenesisConfigService service = GenesisConfigService.fromGenesisConfig(config);

    assertThat(service.isPoaConsensus()).isFalse();
    assertThat(service.getPoaEpochLength()).isEmpty();
    assertThat(service.getConsensusMechanism()).isEqualTo("Ethash");
  }

  @Test
  void shouldPreserveExistingContractAddresses() throws IOException {
    // Geth format with custom contract addresses
    final String gethGenesis =
        "{"
            + "  \"config\": {"
            + "    \"chainId\": 1337,"
            + "    \"londonBlock\": 0,"
            + "    \"mergeNetsplitBlock\": 100,"
            + "    \"withdrawalRequestContractAddress\": \"0x1111111111111111111111111111111111111111\","
            + "    \"consolidationRequestContractAddress\": \"0x2222222222222222222222222222222222222222\""
            + "  },"
            + "  \"difficulty\": \"0x1\","
            + "  \"gasLimit\": \"0x1000000\""
            + "}";

    final File genesisFile = createTempGenesisFile(gethGenesis);
    final GenesisConfigService service =
        GenesisConfigService.fromFile(genesisFile, Collections.emptyMap());

    // Should preserve the custom addresses, not overwrite with defaults
    assertThat(service.getGenesisConfigOptions().getWithdrawalRequestContractAddress())
        .hasValue(
            org.hyperledger.besu.datatypes.Address.fromHexString(
                "0x1111111111111111111111111111111111111111"));
    assertThat(service.getGenesisConfigOptions().getConsolidationRequestContractAddress())
        .hasValue(
            org.hyperledger.besu.datatypes.Address.fromHexString(
                "0x2222222222222222222222222222222222222222"));
  }

  private File createTempGenesisFile(final String content) throws IOException {
    final File genesisFile = new File(tempFolder, "genesis.json");
    Files.writeString(genesisFile.toPath(), content);
    return genesisFile;
  }
}
