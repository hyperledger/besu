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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivGetCodeTest {

  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private final Bytes privacyGroupId =
      Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=");

  private final PrivateTransaction.Builder privateTransactionBuilder =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              Bytes.fromBase64String(
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
          .privateFrom(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
          .restriction(Restriction.RESTRICTED);

  private PrivGetCode method;

  @Mock private BlockchainQueries mockBlockchainQueries;
  @Mock private Blockchain mockBlockchain;
  @Mock private Block mockBlock;
  @Mock private Hash mockHash;
  @Mock private PrivateStateRootResolver mockResolver;
  @Mock private WorldStateArchive mockPrivateWorldStateArchive;
  @Mock private WorldState mockWorldState;
  @Mock private Account mockAccount;

  private final Hash lastStateRoot =
      Hash.fromHexString("0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590");

  private final Bytes contractAccountCode = Bytes.fromBase64String("ZXhhbXBsZQ==");

  @Before
  public void before() {
    method = new PrivGetCode(mockBlockchainQueries, mockPrivateWorldStateArchive, mockResolver);
  }

  @Test
  public void returnValidCodeWhenCalledOnValidContract() {
    final PrivateTransaction transaction =
        privateTransactionBuilder.privacyGroupId(privacyGroupId).signAndBuild(KEY_PAIR);
    final Address contractAddress =
        Address.privateContractAddress(
            transaction.getSender(), transaction.getNonce(), privacyGroupId);

    mockBlockchainWithContractCode(contractAddress);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "priv_getCode",
                new Object[] {
                  "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=",
                  contractAddress.toHexString(),
                  "latest"
                }));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) response).getResult())
        .isEqualTo(contractAccountCode.toString());
  }

  private void mockBlockchainWithContractCode(final Address resultantContractAddress) {
    when(mockBlockchainQueries.getBlockchain()).thenReturn(mockBlockchain);
    when(mockBlockchain.getBlockByNumber(anyLong())).thenReturn(Optional.of(mockBlock));
    when(mockBlock.getHash()).thenReturn(mockHash);
    when(mockResolver.resolveLastStateRoot(any(Bytes32.class), any(Hash.class)))
        .thenReturn(lastStateRoot);
    when(mockPrivateWorldStateArchive.get(lastStateRoot)).thenReturn(Optional.of(mockWorldState));
    when(mockWorldState.get(resultantContractAddress)).thenReturn(mockAccount);
    when(mockAccount.getCode()).thenReturn(contractAccountCode);
  }
}
