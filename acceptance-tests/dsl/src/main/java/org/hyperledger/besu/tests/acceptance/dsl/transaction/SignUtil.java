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
package org.hyperledger.besu.tests.acceptance.dsl.transaction;

import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;

import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;

public class SignUtil {

  private SignUtil() {}

  public static String signTransaction(
      final RawTransaction transaction,
      final Account sender,
      final SignatureAlgorithm signatureAlgorithm) {
    byte[] encodedTransaction = TransactionEncoder.encode(transaction);

    Credentials credentials = sender.web3jCredentialsOrThrow();
    SECPPrivateKey privateKey =
        signatureAlgorithm.createPrivateKey(credentials.getEcKeyPair().getPrivateKey());

    byte[] transactionHash = org.web3j.crypto.Hash.sha3(encodedTransaction);

    SECPSignature secpSignature =
        signatureAlgorithm.sign(
            Bytes32.wrap(transactionHash), signatureAlgorithm.createKeyPair(privateKey));

    Sign.SignatureData signature =
        new Sign.SignatureData(
            // In Ethereum transaction 27 is added to recId (v)
            // See https://ethereum.github.io/yellowpaper/paper.pdf
            //     Appendix F. Signing Transactions (281)
            (byte) (secpSignature.getRecId() + 27),
            secpSignature.getR().toByteArray(),
            secpSignature.getS().toByteArray());
    List<RlpType> values = TransactionEncoder.asRlpValues(transaction, signature);
    RlpList rlpList = new RlpList(values);
    final byte[] encodedSignedTransaction = RlpEncoder.encode(rlpList);

    return Numeric.toHexString(encodedSignedTransaction);
  }
}
