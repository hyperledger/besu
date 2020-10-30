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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.lang.reflect.Field;

import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Test;

public class TransactionEIP1559Test {
  private final RLPInput legacyRLPInput =
      RLP.input(
          Bytes.fromHexString(
              "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884"));
  private final Wei expectedGasPremium = Wei.of(527);
  private final Wei expectedFeeCap = Wei.of(369);

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void givenLegacyTransaction_assertThatRlpEncodingWorks() {
    final Transaction legacyTransaction = Transaction.readFrom(legacyRLPInput);
    assertThat(legacyTransaction.isFrontierTransaction()).isTrue();
    assertThat(legacyTransaction.isEIP1559Transaction()).isFalse();
  }

  @Test
  public void givenEIP1559Transaction_assertThatRlpDecodingWorks() {
    ExperimentalEIPs.eip1559Enabled = true;
    final Transaction legacyTransaction = Transaction.readFrom(legacyRLPInput);
    set(legacyTransaction, "gasPrice", null);
    set(legacyTransaction, "gasPremium", expectedGasPremium);
    set(legacyTransaction, "feeCap", expectedFeeCap);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    legacyTransaction.writeTo(rlpOutput);
    final Transaction eip1559Transaction =
        Transaction.readFrom(new BytesValueRLPInput(rlpOutput.encoded(), false));
    assertThat(eip1559Transaction.isFrontierTransaction()).isFalse();
    assertThat(eip1559Transaction.isEIP1559Transaction()).isTrue();
    assertThat(eip1559Transaction.getGasPremium()).hasValue(expectedGasPremium);
    assertThat(eip1559Transaction.getFeeCap()).hasValue(expectedFeeCap);
    assertThat(eip1559Transaction.getGasPrice()).isEqualByComparingTo(Wei.ZERO);
  }

  private void set(final Object object, final String fieldName, final Object fieldValue) {
    try {
      final Field field = object.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(object, fieldValue);
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }
}
