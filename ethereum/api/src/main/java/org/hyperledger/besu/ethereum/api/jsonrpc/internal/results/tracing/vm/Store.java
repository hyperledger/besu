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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

public class Store {

  private final String key;

  private final String val;

  Store(final String key, final String val) {
    this.key = handle0x(key);
    this.val = handle0x(val);
  }

  public String getKey() {
    return key;
  }

  public String getVal() {
    return val;
  }

  // TODO Remove when https://issues.apache.org/jira/browse/TUWENI-31 is fixed.
  private static String handle0x(final String input) {
    return input.equals("0x") ? "0x0" : input;
  }
}
