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
 *
 */

package org.hyperledger.besu.ethereum.retesteth.methods;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;

import java.io.IOException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

public class TestImportRawBlockTest {
  private TestImportRawBlock test_importRawBlock;
  private TestRewindToBlock test_rewindToBlock;
  private RetestethContext context;

  @Before
  public void setupClass() throws IOException {
    context = new RetestethContext();
    test_importRawBlock = new TestImportRawBlock(context);
    test_rewindToBlock = new TestRewindToBlock(context);
    final TestSetChainParams test_setChainParams = new TestSetChainParams(context);
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
  }

  @Test
  public void testMissingParent() {
    final String rawBlockRLPString =
        "0xf9045df901f9a0e38bef3dadb98e856ea82c7e9813b76a6ec8d9cf60694dd65d800a1669c1a1fda03770bba814f8cc5534ab5e40bdb3fe51866b537805c5577888091766e621fc13948888f1f195afa192cfee860698584c030f4c9db1a019ce64082807650d3d01ac60cd16a583e9472dcc0ccb8f39dd867e317cf025dda09735e49acaddb4d8338ed33df8dd006449b20b85e89e47224ac8ec8f7ea26071a0056b23fbba480696b65fe5a59b8f2148a1299103c4f57df839233af2cf4ca2d2b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000830200000483301fd28252088454c99c2142a00000000000000000000000000000000000000000000000000000000000000000880000000000000000f862f86003018304cb2f94095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba0a7b7f2fa93025fc1e6aa18c1aa07c32a456439754e196cb74f2f7d12cf3e840da02078cf840fb25fc3d858b2a85b622f21be0588b5c5d81d433427f6470e06a4a7f901faf901f7a0f88512d9e022357594866c44ecaa2fc9cb48f34d1987e401109400761aeb898da01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794bcde5374fce5edbc8e2a8697c15331677e6ebf0ba0fe87abb0d3ab38d4eb64405de03db5245b0d40c4b85d8a1b5028ada8643de2dba056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008302000002833007cf808454c9945142a00000000000000000000000000000000000000000000000000000000000000000880000000000000000";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestImportRawBlock.METHOD_NAME, new Object[] {rawBlockRLPString}));

    final var response = test_importRawBlock.response(request);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualTo(JsonRpcError.BLOCK_IMPORT_ERROR);
  }

  @Test
  public void testBadBlock() {
    final String rawBlockRLPString = "0xf9045df901f9a08";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestImportRawBlock.METHOD_NAME, new Object[] {rawBlockRLPString}));

    final var response = test_importRawBlock.response(request);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    assertThat(((JsonRpcErrorResponse) response).getError())
        .isEqualTo(JsonRpcError.BLOCK_RLP_IMPORT_ERROR);
  }

  @Test
  public void testGoodBlock() {
    final String rawBlockRLPString =
        "0xf90262f901faa0e38bef3dadb98e856ea82c7e9813b76a6ec8d9cf60694dd65d800a1669c1a1fda01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347948888f1f195afa192cfee860698584c030f4c9db1a06e6be3f633fe0399cb17a9d8238b988a39bd9ab3e0ac0820f4df705a1ee37536a06fb77a9ddaa64a8e161b643d05533a4093f2be900ad06279b1b56b3bcee3b979a04b33fa3c9c50b7b9a4500f5c0b1e71ab43362abc81c2cf31fd2b54acf7d750d8b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008302000001832fefba83016b66845db7320980a00000000000000000000000000000000000000000000000000000000000000000880000000000000000f862f860800a830249f094095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba0d42a045ac77a6d4676dd5fbc5104ed7471b6cef2465cfefaa52919b340f942a9a06e4d319aea79e45cde79d337e6edf849ceac505cab65dd41a572cab132d4dccac0";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestImportRawBlock.METHOD_NAME, new Object[] {rawBlockRLPString}));

    final var response = test_importRawBlock.response(request);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
  }

  @Test
  public void testReimportExistingBlock() {
    final String rawBlockRLPString =
        "0xf90262f901faa0e38bef3dadb98e856ea82c7e9813b76a6ec8d9cf60694dd65d800a1669c1a1fda01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347948888f1f195afa192cfee860698584c030f4c9db1a06e6be3f633fe0399cb17a9d8238b988a39bd9ab3e0ac0820f4df705a1ee37536a06fb77a9ddaa64a8e161b643d05533a4093f2be900ad06279b1b56b3bcee3b979a04b33fa3c9c50b7b9a4500f5c0b1e71ab43362abc81c2cf31fd2b54acf7d750d8b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008302000001832fefba83016b66845db7320980a00000000000000000000000000000000000000000000000000000000000000000880000000000000000f862f860800a830249f094095e7baea6a6c7c4c2dfeb977efac326af552d870a801ba0d42a045ac77a6d4676dd5fbc5104ed7471b6cef2465cfefaa52919b340f942a9a06e4d319aea79e45cde79d337e6edf849ceac505cab65dd41a572cab132d4dccac0";

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", TestImportRawBlock.METHOD_NAME, new Object[] {rawBlockRLPString}));

    final JsonRpcRequestContext requestRewind =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", TestRewindToBlock.METHOD_NAME, new Object[] {0L}));

    final var response = test_importRawBlock.response(request);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final var rewindResponse = test_rewindToBlock.response(requestRewind);
    assertThat(rewindResponse.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final var reimportResponse = test_importRawBlock.response(request);
    assertThat(reimportResponse.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);

    assertThat(context.getBlockchain().getChainHead().getHeight()).isEqualTo(1L);
  }
}
