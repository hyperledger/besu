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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.util;

import java.util.List;

public class LogFilterJsonParameter {

  private final String fromBlock;
  private final String toBlock;
  private final List<String> addresses;
  private final List<List<String>> topics;
  private final String blockhash;

  public LogFilterJsonParameter(
      final String fromBlock,
      final String toBlock,
      final List<String> addresses,
      final List<List<String>> topics,
      final String blockhash) {
    this.fromBlock = fromBlock;
    this.toBlock = toBlock;
    this.addresses = addresses;
    this.topics = topics;
    this.blockhash = blockhash;
  }

  public String getFromBlock() {
    return fromBlock;
  }

  public String getToBlock() {
    return toBlock;
  }

  public List<String> getAddresses() {
    return addresses;
  }

  public List<List<String>> getTopics() {
    return topics;
  }

  public String getBlockhash() {
    return blockhash;
  }
}
