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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        BytesValue.fromHexString(data),
        Arrays.stream(topics)
            .map(s -> LogTopic.wrap(BytesValue.fromHexString(s)))
            .collect(Collectors.toList()));
  }
}
