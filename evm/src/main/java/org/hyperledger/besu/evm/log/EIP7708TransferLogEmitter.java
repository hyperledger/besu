/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.log;

import static org.apache.tuweni.bytes.Bytes32.leftPad;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import org.apache.tuweni.bytes.Bytes32;

/**
 * EIP-7708 compliant transfer log emitter.
 *
 * <p>Emits a LOG3-equivalent log for all nonzero ETH value transfers with:
 *
 * <ul>
 *   <li>address: SYSTEM_ADDRESS (0xfffffffffffffffffffffffffffffffffffffffe)
 *   <li>topics[0]: Transfer event signature (keccak256('Transfer(address,address,uint256)'))
 *   <li>topics[1]: from address (zero-padded to 32 bytes)
 *   <li>topics[2]: to address (zero-padded to 32 bytes)
 *   <li>data: amount in Wei (big-endian uint256)
 * </ul>
 */
public class EIP7708TransferLogEmitter implements TransferLogEmitter {

  /** Singleton instance for use in Amsterdam+ protocol specs. */
  public static final TransferLogEmitter INSTANCE = new EIP7708TransferLogEmitter();

  /** The system address used as the log emitter for EIP-7708 transfer logs. */
  public static final Address EIP7708_SYSTEM_ADDRESS =
      Address.fromHexString("0xfffffffffffffffffffffffffffffffffffffffe");

  /**
   * The Transfer event signature topic: keccak256('Transfer(address,address,uint256)').
   *
   * <p>This is the same topic used by ERC-20 Transfer events.
   */
  public static final Bytes32 TRANSFER_TOPIC =
      Bytes32.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

  /**
   * The Selfdestruct event signature topic: keccak256('Selfdestruct(address,uint256)').
   *
   * <p>Used for SELFDESTRUCT to self and for account closures with remaining balance.
   */
  public static final Bytes32 SELFDESTRUCT_TOPIC =
      Bytes32.fromHexString("0x4bfaba3443c1a1836cd362418edc679fc96cae8449cbefccb6457cdf2c943083");

  /** Private constructor to enforce singleton usage. */
  private EIP7708TransferLogEmitter() {}

  /**
   * Creates an EIP-7708 compliant transfer log.
   *
   * @param from the sender address
   * @param to the recipient address
   * @param value the amount transferred in Wei
   * @return the transfer log
   */
  public static Log createTransferLog(final Address from, final Address to, final Wei value) {
    // Zero-pad addresses to 32 bytes for topics
    final LogTopic fromTopic = LogTopic.create(leftPad(from.getBytes()));
    final LogTopic toTopic = LogTopic.create(leftPad(to.getBytes()));

    // Value as big-endian uint256 (32 bytes, zero-padded)
    final Bytes32 data = leftPad(value);

    return new Log(
        EIP7708_SYSTEM_ADDRESS,
        data,
        ImmutableList.of(LogTopic.create(TRANSFER_TOPIC), fromTopic, toTopic));
  }

  /**
   * Creates an EIP-7708 compliant selfdestruct log (LOG2).
   *
   * <p>Used when SELFDESTRUCT targets itself or for account closures with remaining balance.
   *
   * @param closedAddress the address of the contract being closed
   * @param value the balance being destroyed in Wei
   * @return the selfdestruct log
   */
  public static Log createSelfdestructLog(final Address closedAddress, final Wei value) {
    // Zero-pad address to 32 bytes for topic
    final LogTopic addressTopic = LogTopic.create(leftPad(closedAddress.getBytes()));

    // Value as big-endian uint256 (32 bytes, zero-padded)
    final Bytes32 data = leftPad(value);

    return new Log(
        EIP7708_SYSTEM_ADDRESS,
        data,
        ImmutableList.of(LogTopic.create(SELFDESTRUCT_TOPIC), addressTopic));
  }

  @Override
  public void emitTransferLog(
      final MessageFrame frame, final Address from, final Address to, final Wei value) {
    if (value.greaterThan(Wei.ZERO)) {
      frame.addLog(createTransferLog(from, to, value));
    }
  }

  @Override
  public void emitSelfDestructLog(
      final MessageFrame frame,
      final Address originator,
      final Address beneficiary,
      final Wei value) {
    if (value.greaterThan(Wei.ZERO)) {
      if (originator.equals(beneficiary)) {
        // SELFDESTRUCT to self → Selfdestruct log (LOG2)
        frame.addLog(createSelfdestructLog(originator, value));
      } else {
        // SELFDESTRUCT to other → Transfer log (LOG3)
        frame.addLog(createTransferLog(originator, beneficiary, value));
      }
    }
  }

  @Override
  public void emitClosureLogs(
      final WorldUpdater worldState,
      final Set<Address> selfDestructs,
      final Consumer<Log> logConsumer) {
    // Collect selfdestruct addresses with nonzero balances, sorted lexicographically
    final List<Map.Entry<Address, Wei>> closures =
        selfDestructs.stream()
            .map(
                addr ->
                    Map.entry(
                        addr,
                        Optional.ofNullable(worldState.get(addr))
                            .map(Account::getBalance)
                            .orElse(Wei.ZERO)))
            .filter(e -> e.getValue().greaterThan(Wei.ZERO))
            .sorted(Comparator.comparing(e -> e.getKey().getBytes().toHexString()))
            .toList();

    // Emit Selfdestruct log for each closure
    closures.forEach(
        entry -> logConsumer.accept(createSelfdestructLog(entry.getKey(), entry.getValue())));
  }
}
