/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.toy.ToyBlockValues;
import org.hyperledger.besu.evm.toy.ToyWorld;

import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class AccessListOperationTracerTest {

  @Test
  void v2ListsTheSameAccessListAsV1() {
    final Random random = new Random(3);
    final Address[] addresses = new Address[3];
    for (int i = 0; i < addresses.length; i++) {
      addresses[i] = Address.wrap(Bytes.random(20, random));
    }
    // access list slots are plain words, the ones SLOAD and SSTORE warm are UInt256
    final Multimap<Address, Bytes32> accessList =
        ImmutableMultimap.of(
            addresses[0], Bytes32.random(random), addresses[2], Bytes32.random(random));
    final MessageFrame v1 = frame(false, accessList);
    final MessageFrame v2 = frame(true, accessList);
    for (int i = 0; i < 200; i++) {
      final Address address = addresses[random.nextInt(addresses.length)];
      final UInt256 slot = UInt256.valueOf(random.nextInt(50));
      v1.warmUpStorage(address, slot);
      v2.warmUpStorageV2(address, slot);
    }

    final List<AccessListEntry> v1List = accessList(v1);

    assertThat(v1List).hasSize(3);
    assertThat(accessList(v2)).isEqualTo(v1List);
  }

  @Test
  void v2ListIsEmptyWithoutWarmSlots() {
    assertThat(accessList(frame(true, ImmutableMultimap.of()))).isEmpty();
  }

  private static List<AccessListEntry> accessList(final MessageFrame frame) {
    final AccessListOperationTracer tracer = AccessListOperationTracer.create();
    tracer.tracePostExecution(frame, null);
    return tracer.getAccessList();
  }

  private static MessageFrame frame(
      final boolean enableEvmV2, final Multimap<Address, Bytes32> accessList) {
    return MessageFrame.builder()
        .worldUpdater(new ToyWorld())
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(new ToyBlockValues())
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(1)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(Bytes32.ZERO)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(Code.EMPTY_CODE)
        .completer(messageFrame -> {})
        .eip2930AccessListWarmStorage(accessList)
        .enableEvmV2(enableEvmV2)
        .build();
  }
}
