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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

public class SignUtil {
  private static final int CHAIN_ID_INC = 35;
  // In Ethereum transaction 27 is added to recId (v)
  // See https://ethereum.github.io/yellowpaper/paper.pdf
  //     Appendix F. Signing Transactions (281)
  private static final int LOWER_REAL_V = 27;

  private SignUtil() {}

  public static byte[] signTransaction(
      final RawTransaction transaction,
      final Account sender,
      final SignatureAlgorithm signatureAlgorithm,
      final Optional<BigInteger> chainId) {
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
            calculateV(secpSignature, chainId),
            secpSignature.getR().toByteArray(),
            secpSignature.getS().toByteArray());
    List<RlpType> values = getTxRlpValues(transaction, signature, secpSignature);
    RlpList rlpList = new RlpList(values);
    byte[] encoded = RlpEncoder.encode(rlpList);

    if (transaction.getType().equals(TransactionType.LEGACY)) {
      return encoded;
    }
    return ByteBuffer.allocate(encoded.length + 1)
        .put(transaction.getType().getRlpType())
        .put(encoded)
        .array();
  }

  private static List<RlpType> getTxRlpValues(
      final RawTransaction transaction,
      final Sign.SignatureData signature,
      final SECPSignature secpSignature) {
    final List<RlpType> values = TransactionEncoder.asRlpValues(transaction, signature);
    if (!transaction.getType().equals(TransactionType.EIP1559)) {
      return values;
    }

    // Fix yParityField for eip1559 txs
    // See outstanding fix: https://github.com/web3j/web3j/pull/1587
    final int yParityFieldIndex = values.size() - 3;
    byte recId = secpSignature.getRecId();
    final byte[] yParityFieldValue =
        recId == 0 ? new byte[] {} : new byte[] {secpSignature.getRecId()};
    final RlpType yParityRlpString = RlpString.create(Bytes.trimLeadingZeroes(yParityFieldValue));
    values.set(yParityFieldIndex, yParityRlpString);
    return values;
  }

  private static byte[] calculateV(
      final SECPSignature secpSignature, final Optional<BigInteger> maybeChainId) {
    byte recId = secpSignature.getRecId();
    return maybeChainId
        .map(
            chainId -> {
              BigInteger v = Numeric.toBigInt(new byte[] {recId});
              v = v.add(chainId.multiply(BigInteger.valueOf(2)));
              v = v.add(BigInteger.valueOf(CHAIN_ID_INC));
              return v.toByteArray();
            })
        .orElseGet(() -> new byte[] {(byte) (recId + LOWER_REAL_V)});
  }
}
