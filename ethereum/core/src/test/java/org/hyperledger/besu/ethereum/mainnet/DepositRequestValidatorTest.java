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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.BLSSignature;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.DepositRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.requests.DepositRequestValidator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidatorCoordinator;
import org.hyperledger.besu.ethereum.mainnet.requests.WithdrawalRequestValidator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DepositRequestValidatorTest {
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private static DepositRequest depositRequest1;
  private static DepositRequest depositRequest2;
  private static Log LOG_1;
  private static Log LOG_2;
  private static Address DEPOSIT_CONTRACT_ADDRESS;
  private static RequestsValidatorCoordinator requestsValidatorCoordinator;
  private static DepositRequestValidator depositRequestValidator;

  @BeforeAll
  public static void setup() {
    depositRequest1 =
        new DepositRequest(
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"),
            Bytes32.fromHexString(
                "0x0017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483"),
            GWei.of(32000000000L),
            BLSSignature.fromHexString(
                "0xa889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb5"),
            UInt64.valueOf(539967));

    depositRequest2 =
        new DepositRequest(
            BLSPublicKey.fromHexString(
                "0x8706d19a62f28a6a6549f96c5adaebac9124a61d44868ec94f6d2d707c6a2f82c9162071231dfeb40e24bfde4ffdf243"),
            Bytes32.fromHexString(
                "0x006a8dc800c6d8dd6977ef53264e2d030350f0145a91bcd167b4f1c3ea21b271"),
            GWei.of(32000000000L),
            BLSSignature.fromHexString(
                "0x801b08ca107b623eca32ee9f9111b4e50eb9cfe19e38204b72de7dc04c5a5e00f61bab96f10842576f66020ce851083f1583dd9a6b73301bea6c245cf51f27cf96aeb018852c5f70bf485d16b957cfe49ca008913346b431e7653ae3ddb23b07"),
            UInt64.valueOf(559887));

    LOG_1 =
        new Log(
            Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa"),
            Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000018000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030b10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483000000000000000000000000000000000000000000000000000000000000000800405973070000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000060a889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb500000000000000000000000000000000000000000000000000000000000000083f3d080000000000000000000000000000000000000000000000000000000000"),
            List.of(
                LogTopic.fromHexString(
                    "0x649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c5")));

    LOG_2 =
        new Log(
            Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa"),
            Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000180000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000308706d19a62f28a6a6549f96c5adaebac9124a61d44868ec94f6d2d707c6a2f82c9162071231dfeb40e24bfde4ffdf243000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020006a8dc800c6d8dd6977ef53264e2d030350f0145a91bcd167b4f1c3ea21b271000000000000000000000000000000000000000000000000000000000000000800405973070000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000060801b08ca107b623eca32ee9f9111b4e50eb9cfe19e38204b72de7dc04c5a5e00f61bab96f10842576f66020ce851083f1583dd9a6b73301bea6c245cf51f27cf96aeb018852c5f70bf485d16b957cfe49ca008913346b431e7653ae3ddb23b0700000000000000000000000000000000000000000000000000000000000000080f8b080000000000000000000000000000000000000000000000000000000000"),
            List.of(
                LogTopic.fromHexString(
                    "0x649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c5")));
    DEPOSIT_CONTRACT_ADDRESS = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    requestsValidatorCoordinator = createAllowDepositValidator();
  }

  @Test
  public void validateAllowedDepositRequests() {
    final List<Request> request = List.of(depositRequest1, depositRequest2);
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequests(Optional.of(request))
            .setRequestsRoot(BodyValidation.requestsRoot(request));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt =
        new TransactionReceipt(null, 0L, List.of(LOG_1, LOG_2), Optional.empty());

    assertThat(requestsValidatorCoordinator.validate(block, Optional.of(request), List.of(receipt)))
        .isTrue();
  }

  @Test
  public void validateAllowedDepositRequestsSeparateReceipts() {

    final List<Request> requests = List.of(depositRequest1, depositRequest2);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequests(Optional.of(requests))
            .setRequestsRoot(BodyValidation.requestsRoot(requests));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_1), Optional.empty());
    final TransactionReceipt receipt2 =
        new TransactionReceipt(null, 0L, List.of(LOG_2), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(
                block, Optional.of(requests), List.of(receipt1, receipt2)))
        .isTrue();
  }

  @Test
  public void invalidateAllowedDepositRequests() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setRequests(Optional.of(List.of(depositRequest1)));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_2), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(block, Optional.of(List.of()), List.of(receipt1)))
        .isFalse();
  }

  @Test
  public void invalidateAllowedDepositRequestsMissingLogInReceipt() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequests(Optional.of(List.of(depositRequest1, depositRequest2)));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_2), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(block, Optional.of(List.of()), List.of(receipt1)))
        .isFalse();
  }

  @Test
  public void invalidateAllowedDepositRequestsExtraLogInReceipt() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setRequests(Optional.of(List.of(depositRequest1)));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_1, LOG_2), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(block, Optional.of(List.of()), List.of(receipt1)))
        .isFalse();
  }

  @Test
  public void invalidateAllowedDepositRequestsWrongOrder() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequests(Optional.of(List.of(depositRequest1, depositRequest2)));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_2, LOG_1), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(block, Optional.of(List.of()), List.of(receipt1)))
        .isFalse();
  }

  @Test
  public void invalidateAllowedDepositRequestsMismatchContractAddress() {

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequests(Optional.of(List.of(depositRequest1, depositRequest2)));
    final Block block = blockDataGenerator.block(blockOptions);

    final TransactionReceipt receipt1 =
        new TransactionReceipt(null, 0L, List.of(LOG_1, LOG_2), Optional.empty());

    assertThat(
            requestsValidatorCoordinator.validate(block, Optional.of(List.of()), List.of(receipt1)))
        .isFalse();
  }

  @Test
  public void validateAllowedDepositParams() {
    final Optional<List<Request>> depositRequests =
        Optional.of(List.of(depositRequest1, depositRequest2));
    assertThat(depositRequestValidator.validateParameter(depositRequests)).isTrue();

    final Optional<List<Request>> emptyDepositRequests = Optional.of(List.of());
    assertThat(depositRequestValidator.validateParameter(emptyDepositRequests)).isTrue();
  }

  @Test
  public void invalidateAllowedDepositParams() {
    final Optional<List<Request>> depositRequests = Optional.empty();
    assertThat(depositRequestValidator.validateParameter(depositRequests)).isFalse();
  }

  static RequestsValidatorCoordinator createAllowDepositValidator() {
    depositRequestValidator = new DepositRequestValidator(DEPOSIT_CONTRACT_ADDRESS);
    return new RequestsValidatorCoordinator.Builder()
        .addValidator(RequestType.WITHDRAWAL, new WithdrawalRequestValidator())
        .addValidator(RequestType.DEPOSIT, depositRequestValidator)
        .build();
  }
}
