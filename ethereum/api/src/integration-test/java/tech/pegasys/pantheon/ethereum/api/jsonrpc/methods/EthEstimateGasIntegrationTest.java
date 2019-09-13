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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.BlockchainImporter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.transaction.CallParameter;
import tech.pegasys.pantheon.testutil.BlockTestUtil;

import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EthEstimateGasIntegrationTest {

  private static JsonRpcTestMethodsFactory BLOCKCHAIN;

  private JsonRpcMethod method;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);

    BLOCKCHAIN =
        new JsonRpcTestMethodsFactory(
            new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson));
  }

  @Before
  public void setUp() {
    final Map<String, JsonRpcMethod> methods = BLOCKCHAIN.methods();
    method = methods.get("eth_estimateGas");
  }

  @Test
  public void shouldReturnExpectedValueForEmptyCallParameter() {
    final CallParameter callParameter = new JsonCallParameter(null, null, null, null, null, null);
    final JsonRpcRequest request = requestWithParams(callParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x5208");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedValueForTransfer() {
    final CallParameter callParameter =
        new JsonCallParameter(
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            "0x8888f1f195afa192cfee860698584c030f4c9db1",
            null,
            null,
            "0x1",
            null);
    final JsonRpcRequest request = requestWithParams(callParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x5208");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedValueForContractDeploy() {
    final CallParameter callParameter =
        new JsonCallParameter(
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            null,
            null,
            null,
            null,
            "0x608060405234801561001057600080fd5b50610157806100206000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633bdab8bf146100515780639ae97baa14610068575b600080fd5b34801561005d57600080fd5b5061006661007f565b005b34801561007457600080fd5b5061007d6100b9565b005b7fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60016040518082815260200191505060405180910390a1565b7fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60026040518082815260200191505060405180910390a17fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60036040518082815260200191505060405180910390a15600a165627a7a7230582010ddaa52e73a98c06dbcd22b234b97206c1d7ed64a7c048e10c2043a3d2309cb0029");
    final JsonRpcRequest request = requestWithParams(callParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x1b551");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldIgnoreGasLimitAndGasPriceAndReturnExpectedValue() {
    final CallParameter callParameter =
        new JsonCallParameter(
            "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f",
            null,
            "0x1",
            "0x9999999999",
            null,
            "0x608060405234801561001057600080fd5b50610157806100206000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633bdab8bf146100515780639ae97baa14610068575b600080fd5b34801561005d57600080fd5b5061006661007f565b005b34801561007457600080fd5b5061007d6100b9565b005b7fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60016040518082815260200191505060405180910390a1565b7fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60026040518082815260200191505060405180910390a17fa53887c1eed04528e23301f55ad49a91634ef5021aa83a97d07fd16ed71c039a60036040518082815260200191505060405180910390a15600a165627a7a7230582010ddaa52e73a98c06dbcd22b234b97206c1d7ed64a7c048e10c2043a3d2309cb0029");
    final JsonRpcRequest request = requestWithParams(callParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x1b551");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedValueForInsufficientGas() {
    final CallParameter callParameter = new JsonCallParameter(null, null, "0x1", null, null, null);
    final JsonRpcRequest request = requestWithParams(callParameter);
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, "0x5208");

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest("2.0", "eth_estimateGas", params);
  }
}
