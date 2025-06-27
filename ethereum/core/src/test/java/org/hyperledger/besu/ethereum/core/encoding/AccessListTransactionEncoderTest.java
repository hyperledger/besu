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
import org.hyperledger.besu.ethereum.core.BlockAccessList;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.core.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.NonceChange;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotChanges;
import org.hyperledger.besu.ethereum.core.BlockAccessList.SlotRead;
import org.hyperledger.besu.ethereum.core.BlockAccessList.StorageChange;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class AccessListTransactionEncoderTest {

  @Test
  void shouldEncodeAndDecodeAccessListWithAllFields() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    final StorageSlotKey slotKey = new StorageSlotKey(Wei.ONE.toUInt256());

    final StorageChange write = new StorageChange(0, Wei.ONE.toUInt256());
    final SlotChanges slotChanges = new SlotChanges(slotKey, List.of(write));
    final SlotRead slotRead = new SlotRead(slotKey);

    final BalanceChange balanceChange = new BalanceChange(0, Wei.fromEth(3).toMinimalBytes());
    final CodeChange codeChange = new CodeChange(1, Bytes.fromHexString("0x6001600101"));
    final NonceChange nonceChange = new NonceChange(2, 42L);

    final AccountChanges accountChanges =
        new AccountChanges(
            address,
            List.of(slotChanges),
            List.of(slotRead),
            List.of(balanceChange),
            List.of(nonceChange),
            List.of(codeChange));

    final BlockAccessList originalAccessList = new BlockAccessList(List.of(accountChanges));

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    BlockAccessListEncoder.encode(originalAccessList, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final BlockAccessList decodedAccessList = BlockAccessListDecoder.decode(input);

    assertThat(decodedAccessList.getAccountChanges()).hasSize(1);
    final AccountChanges decoded = decodedAccessList.getAccountChanges().get(0);

    assertThat(decoded.getAddress()).isEqualTo(address);

    assertThat(decoded.getStorageChanges()).hasSize(1);
    assertThat(decoded.getStorageChanges().get(0).getSlot()).isEqualTo(slotKey);
    assertThat(decoded.getStorageChanges().get(0).getChanges()).hasSize(1);
    assertThat(decoded.getStorageChanges().get(0).getChanges().get(0).getTxIndex()).isEqualTo(0);
    assertThat(decoded.getStorageChanges().get(0).getChanges().get(0).getNewValue())
        .isEqualTo(Wei.ONE.toBytes());

    assertThat(decoded.getStorageReads()).containsExactly(slotRead);

    assertThat(decoded.getBalanceChanges()).containsExactly(balanceChange);
    assertThat(decoded.getNonceChanges()).containsExactly(nonceChange);
    assertThat(decoded.getCodeChanges()).containsExactly(codeChange);
  }

  @Test
  void shouldEncodeAndDecodeAccessListWithEmptyAccountChanges() {
    final BlockAccessList accessList = new BlockAccessList(List.of());

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    BlockAccessListEncoder.encode(accessList, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final BlockAccessList decoded = BlockAccessListDecoder.decode(input);

    assertThat(decoded.getAccountChanges()).isEmpty();
  }
}
