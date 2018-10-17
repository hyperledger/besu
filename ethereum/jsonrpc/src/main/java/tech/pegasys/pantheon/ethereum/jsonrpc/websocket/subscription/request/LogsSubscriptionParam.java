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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class LogsSubscriptionParam {

  private final String address;
  private final List<String> topics;

  @JsonCreator
  LogsSubscriptionParam(
      @JsonProperty("address") final String address,
      @JsonProperty("topics") final List<String> topics) {
    this.address = address;
    this.topics = topics;
  }

  String address() {
    return address;
  }

  List<String> topics() {
    return topics;
  }
}
