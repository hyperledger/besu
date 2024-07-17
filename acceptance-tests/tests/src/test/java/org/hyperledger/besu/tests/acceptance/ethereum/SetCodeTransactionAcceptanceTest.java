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
package org.hyperledger.besu.tests.acceptance.ethereum;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class SetCodeTransactionAcceptanceTest extends AcceptanceTestBase {
  private static final String GENESIS_FILE = "/dev/dev_prague.json";
  private static final SECP256K1 secp256k1 = new SECP256K1();

  public static final Address SEND_ALL_ETH_CONTRACT_ADDRESS =
      Address.fromHexStringStrict("0000000000000000000000000000000000009999");

  private final Account authorizer =
      accounts.createAccount(
          Address.fromHexStringStrict("8da48afC965480220a3dB9244771bd3afcB5d895"));
  public static final Bytes AUTHORIZER_PRIVATE_KEY =
      Bytes.fromHexString("11f2e7b6a734ab03fa682450e0d4681d18a944f8b83c99bf7b9b4de6c0f35ea1");

  private final Account transactionSponsor =
      accounts.createAccount(
          Address.fromHexStringStrict("a05b21E5186Ce93d2a226722b85D6e550Ac7D6E3"));
  public static final Bytes TRANSACTION_SPONSOR_PRIVATE_KEY =
      Bytes.fromHexString("3a4ff6d22d7502ef2452368165422861c01a0f72f851793b372b87888dc3c453");

  private BesuNode besuNode;
  private PragueAcceptanceTestHelper testHelper;

  @BeforeEach
  void setUp() throws IOException {
    besuNode = besu.createExecutionEngineGenesisNode("besuNode", GENESIS_FILE);
    cluster.start(besuNode);

    testHelper = new PragueAcceptanceTestHelper(besuNode, ethTransactions);
  }

  /**
   * At the beginning of the test both the authorizer and the transaction sponsor have a balance of
   * 90000 ETH. The authorizer creates an authorization for a contract that send all its ETH to any
   * given address. The transaction sponsor created a 7702 transaction with it and sends all the ETH
   * from the authorizer to itself. The authorizer balance should be 0 and the transaction sponsor
   * balance should be 180000 ETH minus the transaction costs.
   */
  @Test
  public void shouldTransferAllEthOfAuthorizerToSponsor() throws IOException {

    // 7702 transaction
    final org.hyperledger.besu.datatypes.SetCodeAuthorization authorization =
        SetCodeAuthorization.builder()
            .chainId(BigInteger.valueOf(20211))
            .address(SEND_ALL_ETH_CONTRACT_ADDRESS)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(AUTHORIZER_PRIVATE_KEY.toUnsignedBigInteger())));

    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.SET_CODE)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1000000000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(1000000)
            .to(Address.fromHexStringStrict(authorizer.getAddress()))
            .value(Wei.ZERO)
            .payload(Bytes32.leftPad(Bytes.fromHexString(transactionSponsor.getAddress())))
            .accessList(List.of())
            .setCodeTransactionPayloads(List.of(authorization))
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(
                        TRANSACTION_SPONSOR_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txHash =
        besuNode.execute(ethTransactions.sendRawTransaction(tx.encoded().toHexString()));
    testHelper.buildNewBlock();

    Optional<TransactionReceipt> maybeTransactionReceipt =
        besuNode.execute(ethTransactions.getTransactionReceipt(txHash));
    assertThat(maybeTransactionReceipt).isPresent();

    cluster.verify(authorizer.balanceEquals(0));

    final String gasPriceWithout0x =
        maybeTransactionReceipt.get().getEffectiveGasPrice().substring(2);
    final BigInteger txCost =
        maybeTransactionReceipt.get().getGasUsed().multiply(new BigInteger(gasPriceWithout0x, 16));
    BigInteger expectedSponsorBalance = new BigInteger("180000000000000000000000").subtract(txCost);
    cluster.verify(transactionSponsor.balanceEquals(Amount.wei(expectedSponsorBalance)));
  }
}
