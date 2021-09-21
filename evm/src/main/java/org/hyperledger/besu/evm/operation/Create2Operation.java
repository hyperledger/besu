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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class Create2Operation extends AbstractCreateOperation {

  private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

  public Create2Operation(final GasCalculator gasCalculator) {
    super(0xF5, "CREATE2", 4, 1, false, 1, gasCalculator);
  }

  @Override
  public Address targetContractAddress(final MessageFrame frame) {
    final Address sender = frame.getRecipientAddress();
    final UInt256 offset = frame.getStackItem(1);
    final UInt256 length = frame.getStackItem(2);
    final Bytes32 salt = frame.getStackItem(3);
    final Bytes initCode = frame.readMutableMemory(offset, length);
    final Hash hash = Hash.hash(Bytes.concatenate(PREFIX, sender, salt, Hash.hash(initCode)));
    final Address address = Address.extract(hash);
    frame.warmUpAddress(address);
    return address;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().create2OperationGasCost(frame);
  }
}
