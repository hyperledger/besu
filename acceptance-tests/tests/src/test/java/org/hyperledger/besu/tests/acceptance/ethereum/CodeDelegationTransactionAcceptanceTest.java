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
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class CodeDelegationTransactionAcceptanceTest extends AcceptanceTestBase {
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

  private final Account otherAccount = accounts.createAccount("otherAccount");

  private BesuNode besuNode;
  private PragueAcceptanceTestHelper testHelper;

  @BeforeEach
  void setUp() throws IOException {
    besuNode = besu.createExecutionEngineGenesisNode("besuNode", GENESIS_FILE);
    cluster.start(besuNode);

    testHelper = new PragueAcceptanceTestHelper(besuNode, ethTransactions);
  }

  @AfterEach
  void tearDown() {
    besuNode.close();
  }

  /**
   * At the beginning of the test both the authorizer and the transaction sponsor have a balance of
   * 90000 ETH. The authorizer creates an authorization for a contract that send all its ETH to any
   * given address. The transaction sponsor sponsors the 7702 transaction and sends all the ETH from
   * the authorizer to itself. The authorizer balance should be 0 and the transaction sponsor's
   * balance should be 180000 ETH minus the transaction costs.
   */
  @Test
  public void shouldTransferAllEthOfAuthorizerToSponsor() throws IOException {

    // 7702 transaction
    final CodeDelegation codeDelegation =
        org.hyperledger.besu.ethereum.core.CodeDelegation.builder()
            .chainId(BigInteger.valueOf(20211))
            .address(SEND_ALL_ETH_CONTRACT_ADDRESS)
            .nonce(0)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(AUTHORIZER_PRIVATE_KEY.toUnsignedBigInteger())));

    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.DELEGATE_CODE)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1000000000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(1000000)
            .to(Address.fromHexStringStrict(authorizer.getAddress()))
            .value(Wei.ZERO)
            .payload(Bytes32.leftPad(Bytes.fromHexString(transactionSponsor.getAddress())))
            .accessList(List.of())
            .codeDelegations(List.of(codeDelegation))
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

  /**
   * The authorizer creates an authorization for a contract that sends all its ETH to any given
   * address. The nonce is 1 and the authorization list is processed after the nonce increase of the
   * sender. Therefore, the authorization should be valid. The authorizer balance should be 0 and
   * the transaction sponsor's * balance should be 180000 ETH minus the transaction costs.
   */
  @Test
  public void shouldCheckNonceAfterNonceIncreaseOfSender() throws IOException {
    final long GAS_LIMIT = 1_000_000L;
    cluster.verify(authorizer.balanceEquals(Amount.ether(90_000)));

    final CodeDelegation codeDelegation =
        org.hyperledger.besu.ethereum.core.CodeDelegation.builder()
            .chainId(BigInteger.valueOf(20211))
            .nonce(
                1L) // nonce is 1, but because it is validated before the nonce increase, it should
            // be 0
            .address(SEND_ALL_ETH_CONTRACT_ADDRESS)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(AUTHORIZER_PRIVATE_KEY.toUnsignedBigInteger())));

    final Transaction tx =
        Transaction.builder()
            .type(TransactionType.DELEGATE_CODE)
            .chainId(BigInteger.valueOf(20211))
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(1_000_000_000))
            .maxFeePerGas(Wei.fromHexString("0x02540BE400"))
            .gasLimit(GAS_LIMIT)
            .to(Address.fromHexStringStrict(authorizer.getAddress()))
            .value(Wei.ZERO)
            .payload(Bytes32.leftPad(Bytes.fromHexString(otherAccount.getAddress())))
            .accessList(List.of())
            .codeDelegations(List.of(codeDelegation))
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(AUTHORIZER_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txHash =
        besuNode.execute(ethTransactions.sendRawTransaction(tx.encoded().toHexString()));
    testHelper.buildNewBlock();

    final Optional<TransactionReceipt> maybeFirstTransactionReceipt =
        besuNode.execute(ethTransactions.getTransactionReceipt(txHash));
    assertThat(maybeFirstTransactionReceipt).isPresent();

    final String gasPriceWithout0x =
        maybeFirstTransactionReceipt.get().getEffectiveGasPrice().substring(2);
    final BigInteger gasPrice = new BigInteger(gasPriceWithout0x, 16);
    final BigInteger txCost = maybeFirstTransactionReceipt.get().getGasUsed().multiply(gasPrice);

    final BigInteger authorizerBalanceAfterFirstTx =
        besuNode.execute(ethTransactions.getBalance(authorizer));

    // The remaining balance of the authorizer should the gas limit multiplied by the gas price
    // minus the transaction cost.
    // The following executes this calculation in reverse.
    assertThat(GAS_LIMIT)
        .isEqualTo(authorizerBalanceAfterFirstTx.add(txCost).divide(gasPrice).longValue());

    // The other accounts balance should be the initial 9000 ETH balance from the authorizer minus
    // the remaining balance of the authorizer and minus the transaction cost
    final BigInteger otherAccountBalanceAfterFirstTx =
        new BigInteger("90000000000000000000000")
            .subtract(authorizerBalanceAfterFirstTx)
            .subtract(txCost);

    cluster.verify(otherAccount.balanceEquals(Amount.wei(otherAccountBalanceAfterFirstTx)));

    final Transaction txSendEthToOtherAccount =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.valueOf(20211))
            .nonce(2)
            .maxPriorityFeePerGas(Wei.of(10))
            .maxFeePerGas(Wei.of(100))
            .gasLimit(21_000)
            .to(Address.fromHexStringStrict(otherAccount.getAddress()))
            .value(Wei.ONE)
            .payload(Bytes.EMPTY)
            .signAndBuild(
                secp256k1.createKeyPair(
                    secp256k1.createPrivateKey(AUTHORIZER_PRIVATE_KEY.toUnsignedBigInteger())));

    final String txSendEthToOtherAccountHash =
        besuNode.execute(
            ethTransactions.sendRawTransaction(txSendEthToOtherAccount.encoded().toHexString()));
    testHelper.buildNewBlock();

    final Optional<TransactionReceipt> maybeSecondTransactionReceipt =
        besuNode.execute(ethTransactions.getTransactionReceipt(txSendEthToOtherAccountHash));
    assertThat(maybeSecondTransactionReceipt).isPresent();

    // the balance of the other account should be the previous balance plus the value of the 1 Wei
    final BigInteger otherAccountBalanceAfterSecondTx =
        besuNode.execute(ethTransactions.getBalance(otherAccount));
    assertThat(otherAccountBalanceAfterFirstTx.add(BigInteger.ONE))
        .isEqualTo(otherAccountBalanceAfterSecondTx);
  }
}
