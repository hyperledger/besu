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

import com.google.common.base.Preconditions;

/** Encapsulates a group of {@link Operation}s used together. */
public class OperationRegistry {

  private static final int NUM_OPERATIONS = 256;

  private final Operation[][] operations;

  public OperationRegistry() {
    this(1);
  }

  public OperationRegistry(final int numVersions) {
    Preconditions.checkArgument(numVersions >= 1);
    this.operations = new Operation[numVersions][NUM_OPERATIONS];
  }

  public Operation get(final byte opcode, final int version) {
    return get(opcode & 0xff, version);
  }

  public Operation get(final int opcode, final int version) {
    return operations[version][opcode];
  }

  public void put(final Operation operation, final int version) {
    operations[version][operation.getOpcode()] = operation;
  }

  public Operation getOrDefault(
      final byte opcode, final int version, final Operation defaultOperation) {
    final Operation operation = get(opcode, version);

    if (operation == null) {
      return defaultOperation;
    }

    return operation;
  }
}
