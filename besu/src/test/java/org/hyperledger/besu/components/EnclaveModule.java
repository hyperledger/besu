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
package org.hyperledger.besu.components;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

@Module
public class EnclaveModule {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private static final Bytes ENCLAVE_PUBLIC_KEY =
      Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  private static final Bytes32 PRIVACY_GROUP_BYTES32 =
      Bytes32.fromHexString("0xf250d523ae9164722b06ca25cfa2a7f3c45df96b09e215236f886c876f715bfa");

  private static final Bytes MOCK_PAYLOAD =
      Bytes.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610199806100326000396000f3fe6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");

  private static final KeyPair KEY_PAIR =
      SIGNATURE_ALGORITHM
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private static final PrivateTransaction PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .chainId(BigInteger.valueOf(1337))
          .gasLimit(1000)
          .gasPrice(Wei.ZERO)
          .nonce(0)
          .payload(MOCK_PAYLOAD)
          .to(null)
          .privateFrom(ENCLAVE_PUBLIC_KEY)
          .privateFor(Collections.singletonList(ENCLAVE_PUBLIC_KEY))
          .restriction(Restriction.RESTRICTED)
          .value(Wei.ZERO)
          .signAndBuild(KEY_PAIR);

  @Provides
  EnclaveFactory provideMockableEnclaveFactory() {
    Enclave mockEnclave = mock(Enclave.class);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    PRIVATE_TRANSACTION.writeTo(rlpOutput);
    when(mockEnclave.receive(any()))
        .thenReturn(
            new ReceiveResponse(
                rlpOutput.encoded().toBase64String().getBytes(StandardCharsets.UTF_8),
                PRIVACY_GROUP_BYTES32.toBase64String(),
                ENCLAVE_PUBLIC_KEY.toBase64String()));
    EnclaveFactory enclaveFactory = mock(EnclaveFactory.class);
    when(enclaveFactory.createVertxEnclave(any())).thenReturn(mockEnclave);
    return enclaveFactory;
  }
}
