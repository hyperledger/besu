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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor.Result;
import org.hyperledger.besu.ethereum.privacy.Restriction;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

public class PrivateTransactionDataFixture {

  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  static Transaction privacyMarkerTransaction(final String transactionKey) {
    return Transaction.builder()
        .nonce(0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.DEFAULT_PRIVACY)
        .value(Wei.ZERO)
        .payload(Bytes.fromBase64String(transactionKey))
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(2018))
        .signAndBuild(KEY_PAIR);
  }

  static PrivateTransaction privateTransaction(final String privacyGroupId) {
    return PrivateTransaction.builder()
        .nonce(0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(null)
        .value(Wei.ZERO)
        .payload(
            Bytes.fromHexString(
                "0x608060405234801561001057600080fd5b5060d08061001f6000396000"
                    + "f3fe60806040526004361060485763ffffffff7c010000000000"
                    + "0000000000000000000000000000000000000000000000600035"
                    + "04166360fe47b18114604d5780636d4ce63c146075575b600080"
                    + "fd5b348015605857600080fd5b50607360048036036020811015"
                    + "606d57600080fd5b50356099565b005b348015608057600080fd"
                    + "5b506087609e565b60408051918252519081900360200190f35b"
                    + "600055565b6000549056fea165627a7a72305820cb1d0935d14b"
                    + "589300b12fcd0ab849a7e9019c81da24d6daa4f6b2f003d1b018"
                    + "0029"))
        .sender(Address.wrap(Bytes.fromHexString("0x1c9a6e1ee3b7ac6028e786d9519ae3d24ee31e79")))
        .chainId(BigInteger.valueOf(4))
        .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
        .privacyGroupId(Bytes.fromBase64String(privacyGroupId))
        .restriction(Restriction.RESTRICTED)
        .signAndBuild(KEY_PAIR);
  }

  public static Result successfulPrivateTxProcessingResult() {
    return Result.successful(
        blockDataGenerator.logs(3, 1), 0, Bytes.EMPTY, ValidationResult.valid());
  }
}
