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
package org.hyperledger.besu.ethereum.retesteth.methods;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;

import java.io.IOException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestSetChainParamsTest {

  private static RetestethContext context;
  private static TestSetChainParams test_setChainParams;

  @BeforeClass
  public static void setupClass() {
    context = new RetestethContext();
    test_setChainParams = new TestSetChainParams(context);
  }

  @Test
  public void testValidateGenesisImport() throws IOException {
    final String chainParamsJsonString =
        Resources.toString(
            TestSetChainParamsTest.class.getResource("multimpleBalanceInstructionChainParams.json"),
            Charsets.UTF_8);
    final JsonObject chainParamsJson = new JsonObject(chainParamsJsonString);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestSetChainParams.METHOD_NAME, new Object[] {chainParamsJson.getMap()}));

    assertThat(test_setChainParams.response(request))
        .isEqualTo(new JsonRpcSuccessResponse(null, true));

    final BlockHeader blockHeader = context.getBlockHeader(0);

    assertThat(blockHeader.getLogsBloom().toString())
        .isEqualTo(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    assertThat(blockHeader.getCoinbase().toString())
        .isEqualTo("0x8888f1f195afa192cfee860698584c030f4c9db1");
    assertThat(blockHeader.getDifficulty()).isEqualTo(UInt256.fromHexString("0x20000"));
    assertThat(blockHeader.getExtraData().toHexString()).isEqualTo("0x42");
    assertThat(blockHeader.getGasLimit()).isEqualTo(3141592);
    assertThat(blockHeader.getGasUsed()).isEqualTo(0);
    assertThat(blockHeader.getMixHash().toString())
        .isEqualTo("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    assertThat(blockHeader.getNonce()).isEqualTo(0x0102030405060708L);
    assertThat(blockHeader.getNumber()).isEqualTo(0);
    assertThat(blockHeader.getParentHash().toString())
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    assertThat(blockHeader.getReceiptsRoot().toString())
        .isEqualTo("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    assertThat(blockHeader.getStateRoot().toString())
        .isEqualTo("0xf403922bfd555a9223f68fc755564004e20d78bb42aae647e867e3b23c48beba");
    assertThat(blockHeader.getTimestamp()).isEqualTo(0x54c98c81);
    assertThat(blockHeader.getTransactionsRoot().toString())
        .isEqualTo("0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
    assertThat(blockHeader.getOmmersHash().toString())
        .isEqualTo("0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347");
  }

  @Test
  public void testValidate1559GenesisImport() throws IOException {
    final String chainParamsJsonString =
        Resources.toString(
            TestSetChainParamsTest.class.getResource("1559ChainParams.json"), Charsets.UTF_8);
    final JsonObject chainParamsJson = new JsonObject(chainParamsJsonString);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestSetChainParams.METHOD_NAME, new Object[] {chainParamsJson.getMap()}));

    assertThat(test_setChainParams.response(request))
        .isEqualTo(new JsonRpcSuccessResponse(null, true));

    final BlockHeader blockHeader = context.getBlockHeader(0);
    assertThat(blockHeader.getDifficulty()).isEqualTo(UInt256.fromHexString("0x20000"));
    assertThat(blockHeader.getGasLimit()).isEqualTo(1234L);
    assertThat(blockHeader.getBaseFee()).hasValue(Wei.of(12345L));
    assertThat(blockHeader.getExtraData().toHexString()).isEqualTo("0x00");
    assertThat(blockHeader.getTimestamp()).isEqualTo(0l);
    assertThat(blockHeader.getNonce()).isEqualTo(0L);
    assertThat(blockHeader.getMixHash().toHexString())
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
  }
}
