/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.Restriction;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PrivateTransactionBuilder {

  public static BytesValue EVENT_EMITTER_CONSTRUCTOR =
      BytesValue.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a06"
              + "0020a03191633179055610199806100326000396000f3fe6080"
              + "604052600436106100565763ffffffff7c01000000000000000"
              + "000000000000000000000000000000000000000006000350416"
              + "633fa4f245811461005b5780636057361d1461008257806367e"
              + "404ce146100ae575b600080fd5b34801561006757600080fd5b"
              + "506100706100ec565b60408051918252519081900360200190f"
              + "35b34801561008e57600080fd5b506100ac6004803603602081"
              + "10156100a557600080fd5b50356100f2565b005b3480156100b"
              + "a57600080fd5b506100c3610151565b6040805173ffffffffff"
              + "ffffffffffffffffffffffffffffff909216825251908190036"
              + "0200190f35b60025490565b6040805133815260208101839052"
              + "81517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb1"
              + "2b753f3d1aaa2d8f9f5929181900390910190a1600255600180"
              + "5473ffffffffffffffffffffffffffffffffffffffff1916331"
              + "79055565b60015473ffffffffffffffffffffffffffffffffff"
              + "ffffff169056fea165627a7a72305820c7f729cb24e05c221f5"
              + "aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");

  private static BytesValue SET_FUNCTION_CALL =
      BytesValue.fromHexString(
          "0x6057361d00000000000000000000000000000000000000000000000000000000000003e8");

  private static BytesValue GET_FUNCTION_CALL = BytesValue.fromHexString("0x3fa4f245");

  public enum TransactionType {
    CREATE_CONTRACT,
    STORE,
    GET
  }

  public static PrivateTransactionBuilder.Builder builder() {
    return new PrivateTransactionBuilder.Builder();
  }

  public static class Builder {
    long nonce;
    Address from;
    Address to;
    BytesValue privateFrom;
    Optional<BytesValue> privacyGroupId = Optional.empty();
    Optional<List<BytesValue>> privateFor = Optional.of(new ArrayList<>());
    SECP256K1.KeyPair keyPair;

    public Builder nonce(final long nonce) {
      this.nonce = nonce;
      return this;
    }

    public Builder from(final Address from) {
      this.from = from;
      return this;
    }

    public Builder to(final Address to) {
      this.to = to;
      return this;
    }

    public Builder privateFrom(final BytesValue privateFrom) {
      this.privateFrom = privateFrom;
      return this;
    }

    public Builder privacyGroupId(final BytesValue privacyGroupId) {
      this.privacyGroupId = Optional.of(privacyGroupId);
      return this;
    }

    public Builder privateFor(final List<BytesValue> privateFor) {
      this.privateFor = Optional.of(privateFor);
      return this;
    }

    public Builder keyPair(final SECP256K1.KeyPair keyPair) {
      this.keyPair = keyPair;
      return this;
    }

    public String build(final TransactionType type) {
      BytesValue payload;
      switch (type) {
        case CREATE_CONTRACT:
          payload = EVENT_EMITTER_CONSTRUCTOR;
          break;
        case STORE:
          payload = SET_FUNCTION_CALL;
          break;
        case GET:
          payload = GET_FUNCTION_CALL;
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + type);
      }

      var builder =
          PrivateTransaction.builder()
              .nonce(nonce)
              .gasPrice(Wei.of(1000))
              .gasLimit(63992)
              .to(to)
              .value(Wei.ZERO)
              .payload(payload)
              .sender(from)
              .chainId(BigInteger.valueOf(2018))
              .privateFrom(privateFrom)
              .restriction(Restriction.RESTRICTED);

      if (privacyGroupId.isPresent()) {
        builder = builder.privacyGroupId(privacyGroupId.get());
      } else {
        builder = builder.privateFor(privateFor.get());
      }

      return RLP.encode(builder.signAndBuild(keyPair)::writeTo).toString();
    }
  }
}
