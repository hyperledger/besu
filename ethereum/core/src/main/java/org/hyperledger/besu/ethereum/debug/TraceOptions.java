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
package org.hyperledger.besu.ethereum.debug;

public class TraceOptions {

  private final boolean traceStorage;
  private final boolean traceMemory;
  private final boolean traceStack;

  public static final TraceOptions DEFAULT = new TraceOptions(true, true, true);

  public TraceOptions(
      final boolean traceStorage, final boolean traceMemory, final boolean traceStack) {
    this.traceStorage = traceStorage;
    this.traceMemory = traceMemory;
    this.traceStack = traceStack;
  }

  public boolean isStorageEnabled() {
    return traceStorage;
  }

  public boolean isMemoryEnabled() {
    return traceMemory;
  }

  public boolean isStackEnabled() {
    return traceStack;
  }
}
