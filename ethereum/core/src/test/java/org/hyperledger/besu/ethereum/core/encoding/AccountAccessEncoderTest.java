/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.PerTxAccess;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotAccess;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class AccountAccessEncoderTest {

  @Test
  void shouldEncodeAndDecodeAccountAccess() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    final StorageSlotKey slotKey = new StorageSlotKey(Wei.fromEth(1).toUInt256());
    final PerTxAccess access1 = new PerTxAccess(0, Optional.of(Bytes.fromHexString("0xdeadbeef")));
    final PerTxAccess access2 = new PerTxAccess(1, Optional.empty());
    final PerTxAccess access3 = new PerTxAccess(2, Optional.of(Wei.ZERO.toBytes()));
    final SlotAccess slotAccess = new SlotAccess(slotKey, List.of(access1, access2, access3));
    final AccountAccess original = new AccountAccess(address, List.of(slotAccess));

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    AccountAccessEncoder.encode(original, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final AccountAccess decoded = AccountAccessDecoder.decode(input);

    assertThat(decoded.getAddress()).isEqualTo(address);
    assertThat(decoded.getSlotAccesses()).hasSize(1);

    final SlotAccess decodedSlot = decoded.getSlotAccesses().get(0);
    assertThat(decodedSlot.getSlot().getSlotKey()).isEqualTo(slotKey.getSlotKey());

    final List<PerTxAccess> accesses = decodedSlot.getPerTxAccesses();
    assertThat(accesses).hasSize(3);
    assertThat(accesses.get(0).getTxIndex()).isEqualTo(0);
    assertThat(accesses.get(0).getValueAfter()).contains(Bytes.fromHexString("0xdeadbeef"));
    assertThat(accesses.get(1).getTxIndex()).isEqualTo(1);
    assertThat(accesses.get(1).getValueAfter()).isEmpty();
    assertThat(accesses.get(2).getTxIndex()).isEqualTo(2);
    assertThat(accesses.get(2).getValueAfter()).contains(Wei.ZERO.toBytes());
  }

  @Test
  void shouldEncodeAndDecodeAccountAccessWithEmptySlotAccesses() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    final AccountAccess original = new AccountAccess(address, List.of());

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    AccountAccessEncoder.encode(original, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final AccountAccess decoded = AccountAccessDecoder.decode(input);

    assertThat(decoded.getAddress()).isEqualTo(address);
    assertThat(decoded.getSlotAccesses()).isEmpty();
  }
}
