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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class LogMock extends Log {

  /**
   * Represents a mock log object for testing. Populated from json data.
   *
   * @param address The address of the account generating the log.
   * @param data The data associated with the log.
   * @param topics The topics associated with the log.
   */
  @JsonCreator
  public LogMock(
      @JsonProperty("address") final String address,
      @JsonProperty("data") final String data,
      @JsonProperty("topics") final String[] topics) {
    super(
        Address.fromHexString(address),
        Bytes.fromHexString(data),
        Arrays.stream(topics).map(LogTopic::fromHexString).collect(Collectors.toList()));
  }
}
