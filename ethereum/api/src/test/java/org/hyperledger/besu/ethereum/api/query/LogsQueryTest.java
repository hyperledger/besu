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
package org.hyperledger.besu.ethereum.api.query;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class LogsQueryTest {

  private static final Address FIRST_ADDRESS =
      Address.fromHexString("8320fe7702b96808f7bbc0d4a888ed1468216cfd");
  private static final LogTopic FIRST_ADDRESS_TOPIC =
      LogTopic.fromHexString("0000000000000000000000008320fe7702b96808f7bbc0d4a888ed1468216cfd");
  private static final LogTopic SECOND_ADDRESS_TOPIC =
      LogTopic.fromHexString("0000000000000000000000009320fe7702b96808f7bbc0d4a888ed1468216cfd");
  private static final LogTopic ERC20_TRANSFER_EVENT =
      LogTopic.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

  @Test
  public void testMatches() {
    final LogsQuery query =
        new LogsQuery(
            singletonList(FIRST_ADDRESS),
            List.of(
                singletonList(ERC20_TRANSFER_EVENT),
                List.of(FIRST_ADDRESS_TOPIC, SECOND_ADDRESS_TOPIC)));

    assertThat(query.matches(new Log(FIRST_ADDRESS, Bytes.EMPTY, List.of()))).isFalse();
    assertThat(query.matches(new Log(FIRST_ADDRESS, Bytes.EMPTY, List.of(ERC20_TRANSFER_EVENT))))
        .isFalse();
    assertThat(
            query.matches(
                new Log(
                    FIRST_ADDRESS,
                    Bytes.EMPTY,
                    List.of(ERC20_TRANSFER_EVENT, FIRST_ADDRESS_TOPIC))))
        .isTrue();
    assertThat(
            query.matches(
                new Log(
                    FIRST_ADDRESS,
                    Bytes.EMPTY,
                    List.of(ERC20_TRANSFER_EVENT, SECOND_ADDRESS_TOPIC))))
        .isTrue();
    assertThat(
            query.matches(
                new Log(
                    FIRST_ADDRESS,
                    Bytes.EMPTY,
                    List.of(ERC20_TRANSFER_EVENT, SECOND_ADDRESS_TOPIC, FIRST_ADDRESS_TOPIC))))
        .isTrue();
  }
}
