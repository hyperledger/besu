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
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner;

import java.math.BigInteger;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.web3j.utils.Numeric;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrivateTransactionRequest {

  private final String from;
  private final BigInteger nonce;
  private final BigInteger gasPrice;
  private final BigInteger gas;
  private final String to;
  private final BigInteger value;
  private final String data;
  private final String privateFrom;
  private final List<String> privateFor;
  private final String restriction;

  public PrivateTransactionRequest(
      final String from,
      final BigInteger nonce,
      final BigInteger gasPrice,
      final BigInteger gasLimit,
      final String to,
      final BigInteger value,
      final String data,
      final String privateFrom,
      final List<String> privateFor,
      final String restriction) {
    this.from = from;
    this.to = to;
    this.gas = gasLimit;
    this.gasPrice = gasPrice;
    this.value = value;
    this.data = data == null ? null : Numeric.prependHexPrefix(data);
    this.nonce = nonce;
    this.privateFrom = privateFrom;
    this.privateFor = privateFor;
    this.restriction = restriction;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getGas() {
    return convert(gas);
  }

  public String getGasPrice() {
    return convert(gasPrice);
  }

  public String getValue() {
    return convert(value);
  }

  public String getData() {
    return data;
  }

  public String getNonce() {
    return convert(nonce);
  }

  private String convert(final BigInteger value) {
    return value == null ? null : Numeric.encodeQuantity(value);
  }

  public String getPrivateFrom() {
    return privateFrom;
  }

  public List<String> getPrivateFor() {
    return privateFor;
  }

  public String getRestriction() {
    return restriction;
  }
}
