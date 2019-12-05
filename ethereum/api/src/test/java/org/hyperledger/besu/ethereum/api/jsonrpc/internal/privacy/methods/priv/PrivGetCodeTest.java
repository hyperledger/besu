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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PrivGetCodeTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private final Address sender =
          Address.fromHexString("0x0000000000000000000000000000000000000003");
  private static final SECP256K1.KeyPair KEY_PAIR =
          SECP256K1.KeyPair.create(
                  SECP256K1.PrivateKey.create(
                          new BigInteger(
                                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private final PrivateTransaction.Builder privateTransactionBuilder =
          PrivateTransaction.builder()
                  .nonce(0)
                  .gasPrice(Wei.of(1000))
                  .gasLimit(3000000)
                  .to(null)
                  .value(Wei.ZERO)
                  .payload(
                          BytesValue.fromHexString(
                                  "0x608060405234801561001057600080fd5b5060d08061001f60003960"
                                          + "00f3fe60806040526004361060485763ffffffff7c01000000"
                                          + "00000000000000000000000000000000000000000000000000"
                                          + "60003504166360fe47b18114604d5780636d4ce63c14607557"
                                          + "5b600080fd5b348015605857600080fd5b5060736004803603"
                                          + "6020811015606d57600080fd5b50356099565b005b34801560"
                                          + "8057600080fd5b506087609e565b6040805191825251908190"
                                          + "0360200190f35b600055565b6000549056fea165627a7a7230"
                                          + "5820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6"
                                          + "daa4f6b2f003d1b0180029"))
                  .sender(sender)
                  .chainId(BigInteger.valueOf(2018))
                  .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
                  .restriction(Restriction.RESTRICTED);

  private final String enclaveKey =
          BytesValues.fromBase64("93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=").toString();

  private final Enclave enclave = mock(Enclave.class);

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);;
  private final Blockchain mockBlockchain = mock(Blockchain.class);
  private final Block mockBlock = mock(Block.class);
  private final WorldStateArchive mockArchive = mock(WorldStateArchive.class);
  private PrivGetCode privGetCode;

  @Before
  public void before() {
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.getPrivateWorldStateArchive()).thenReturn(mockArchive);
    when(blockchain.getBlockchain()).thenReturn(mockBlockchain);
    when(mockBlockchain.getChainHeadBlock()).thenReturn(mockBlock);
    this.privGetCode = new PrivGetCode(blockchain, privacyParameters);
  }

  @Test
  public void returnPrivacyDisabledErrorWhenPrivacyIsDisabled() {
    PrivateTransaction transaction = privateTransactionBuilder.privacyGroupId(BytesValues.fromBase64("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="))
            .signAndBuild(KEY_PAIR);


    final JsonRpcRequestContext request =
            new JsonRpcRequestContext(new JsonRpcRequest("2.0", "priv_getCode", new Object[] {transaction.getSender().toString(), enclaveKey}));

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privGetCode.response(request);

    assertThat(response.getError()).isEqualTo(JsonRpcError.PRIVACY_NOT_ENABLED);
  }
}
