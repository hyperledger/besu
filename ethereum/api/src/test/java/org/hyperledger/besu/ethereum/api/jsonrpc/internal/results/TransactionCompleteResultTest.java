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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class TransactionCompleteResultTest {
  @Test
  public void accessListTransactionFields() throws JsonProcessingException {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Transaction transaction = gen.transaction(TransactionType.ACCESS_LIST);
    final TransactionCompleteResult transactionCompleteResult =
        new TransactionCompleteResult(
            new TransactionWithMetadata(
                transaction,
                136,
                Hash.fromHexString(
                    "0xfc84c3946cb419cbd8c2c68d5e79a3b2a03a8faff4d9e2be493f5a07eb5da95e"),
                0));

    final ObjectMapper objectMapper = new ObjectMapper();
    final String jsonString =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transactionCompleteResult);

    assertThat(jsonString)
        .startsWith(
            "{\n"
                + "  \"accessList\" : [ {\n"
                + "    \"address\" : \"0x47902028e61cfdc243d9d16008aabc9fb77cc723\",\n"
                + "    \"storageKeys\" : [ ]\n"
                + "  }, {\n"
                + "    \"address\" : \"0xa56017e14f1ce8b1698341734a6823ce02043e01\",\n"
                + "    \"storageKeys\" : [ \"0x6b544901214a2ddab82fec85c0b9fe0549c475be5b887bb4b8995b24fb5c6846\", \"0xf88b527b4f9d4c1391f1678b23ba4f9c9cd7bc93eb5776f4f036753448642946\" ]\n"
                + "  } ],");
  }
}
