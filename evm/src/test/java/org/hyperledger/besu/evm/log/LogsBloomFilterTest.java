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
package org.hyperledger.besu.evm.log;

import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class LogsBloomFilterTest {

  @Test
  public void logsBloomFilter() {
    final Address address = Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87");
    final Bytes data = Bytes.fromHexString("0x0102");
    final List<LogTopic> topics = new ArrayList<>();
    topics.add(
        LogTopic.fromHexString(
            "0x0000000000000000000000000000000000000000000000000000000000000000"));

    final Log log = new Log(address, data, topics);
    final LogsBloomFilter bloom = LogsBloomFilter.builder().insertLog(log).build();

    Assertions.assertThat(bloom)
        .isEqualTo(
            Bytes.fromHexString(
                "0x00000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000040000000000000000000000000000000000000000000000000000000"));
  }
}
