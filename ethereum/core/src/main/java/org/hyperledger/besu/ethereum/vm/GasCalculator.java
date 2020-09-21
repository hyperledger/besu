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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.AbstractMessageProcessor;
import org.hyperledger.besu.ethereum.mainnet.precompiles.ECRECPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.IDPrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.RIPEMD160PrecompiledContract;
import org.hyperledger.besu.ethereum.mainnet.precompiles.SHA256PrecompiledContract;
import org.hyperledger.besu.ethereum.vm.operations.BalanceOperation;
import org.hyperledger.besu.ethereum.vm.operations.BeginSubOperation;
import org.hyperledger.besu.ethereum.vm.operations.BlockHashOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExpOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeCopyOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeHashOperation;
import org.hyperledger.besu.ethereum.vm.operations.ExtCodeSizeOperation;
import org.hyperledger.besu.ethereum.vm.operations.JumpDestOperation;
import org.hyperledger.besu.ethereum.vm.operations.LogOperation;
import org.hyperledger.besu.ethereum.vm.operations.MLoadOperation;
import org.hyperledger.besu.ethereum.vm.operations.MStore8Operation;
import org.hyperledger.besu.ethereum.vm.operations.MStoreOperation;
import org.hyperledger.besu.ethereum.vm.operations.SLoadOperation;
import org.hyperledger.besu.ethereum.vm.operations.SelfDestructOperation;
import org.hyperledger.besu.ethereum.vm.operations.Sha3Operation;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Provides various gas cost lookups and calculations used during block processing.
 *
 * <p>The {@code GasCalculator} is meant to encapsulate all {@link Gas}-related calculations except
 * for the following "safe" operations:
 *
 * <ul>
 *   <li><b>Operation Gas Deductions:</b> Deducting the operation's gas cost from the VM's current
 *       message frame because the
 * </ul>
 */
public interface GasCalculator {

  // Transaction Gas Calculations

  /**
   * Returns a {@link Transaction}s intrinisic gas cost
   *
   * @param transaction The transaction
   * @return the transaction's intrinsic gas cost
   */
  Gas transactionIntrinsicGasCost(Transaction transaction);

  // Contract Creation Gas Calculations

  /**
   * Returns the cost for a {@link AbstractMessageProcessor} to deposit the code in storage
   *
   * @param codeSize The size of the code in bytes
   * @return the code deposit cost
   */
  Gas codeDepositGasCost(int codeSize);

  // Precompiled Contract Gas Calculations

  /**
   * Returns the gas cost to execute the {@link IDPrecompiledContract}.
   *
   * @param input The input to the ID precompiled contract
   * @return the gas cost to execute the ID precompiled contract
   */
  Gas idPrecompiledContractGasCost(Bytes input);

  /**
   * Returns the gas cost to execute the {@link ECRECPrecompiledContract}.
   *
   * @return the gas cost to execute the ECREC precompiled contract
   */
  Gas getEcrecPrecompiledContractGasCost();

  /**
   * Returns the gas cost to execute the {@link SHA256PrecompiledContract}.
   *
   * @param input The input to the SHA256 precompiled contract
   * @return the gas cost to execute the SHA256 precompiled contract
   */
  Gas sha256PrecompiledContractGasCost(Bytes input);

  /**
   * Returns the gas cost to execute the {@link RIPEMD160PrecompiledContract}.
   *
   * @param input The input to the RIPEMD160 precompiled contract
   * @return the gas cost to execute the RIPEMD160 precompiled contract
   */
  Gas ripemd160PrecompiledContractGasCost(Bytes input);

  // Gas Tier Lookups

  /**
   * Returns the gas cost for the zero gas tier.
   *
   * @return the gas cost for the zero gas tier
   */
  Gas getZeroTierGasCost();

  /**
   * Returns the gas cost for the very low gas tier.
   *
   * @return the gas cost for the very low gas tier
   */
  Gas getVeryLowTierGasCost();

  /**
   * Returns the gas cost for the low gas tier.
   *
   * @return the gas cost for the low gas tier
   */
  Gas getLowTierGasCost();

  /**
   * Returns the gas cost for the base gas tier.
   *
   * @return the gas cost for the base gas tier
   */
  Gas getBaseTierGasCost();

  /**
   * Returns the gas cost for the mid gas tier.
   *
   * @return the gas cost for the mid gas tier
   */
  Gas getMidTierGasCost();

  /**
   * Returns the gas cost for the high gas tier.
   *
   * @return the gas cost for the high gas tier
   */
  Gas getHighTierGasCost();

  // Call/Create Operation Calculations

  /**
   * Returns the gas cost for one of the various CALL operations.
   *
   * @param frame The current frame
   * @param stipend The gas stipend being provided by the CALL caller
   * @param inputDataOffset The offset in memory to retrieve the CALL input data
   * @param inputDataLength The CALL input data length
   * @param outputDataOffset The offset in memory to place the CALL output data
   * @param outputDataLength The CALL output data length
   * @param transferValue The wei being transferred
   * @param recipient The CALL recipient
   * @return The gas cost for the CALL operation
   */
  Gas callOperationGasCost(
      MessageFrame frame,
      Gas stipend,
      UInt256 inputDataOffset,
      UInt256 inputDataLength,
      UInt256 outputDataOffset,
      UInt256 outputDataLength,
      Wei transferValue,
      Account recipient);

  Gas getAdditionalCallStipend();

  /**
   * Returns the amount of gas parent will provide its child CALL.
   *
   * @param frame The current frame
   * @param stipend The gas stipend being provided by the CALL caller
   * @param transfersValue Whether or not the call transfers any wei
   * @return the amount of gas parent will provide its child CALL
   */
  Gas gasAvailableForChildCall(MessageFrame frame, Gas stipend, boolean transfersValue);

  /**
   * Returns the amount of gas the CREATE operation will consume.
   *
   * @param frame The current frame
   * @return the amount of gas the CREATE operation will consume
   */
  Gas createOperationGasCost(MessageFrame frame);

  /**
   * Returns the amount of gas the CREATE2 operation will consume.
   *
   * @param frame The current frame
   * @return the amount of gas the CREATE2 operation will consume
   */
  Gas create2OperationGasCost(MessageFrame frame);

  /**
   * Returns the amount of gas parent will provide its child CREATE.
   *
   * @param stipend The gas stipend being provided by the CREATE caller
   * @return the amount of gas parent will provide its child CREATE
   */
  Gas gasAvailableForChildCreate(Gas stipend);

  // Re-used Operation Calculations

  /**
   * Returns the amount of gas consumed by the data copy operation.
   *
   * @param frame The current frame
   * @param offset The offset in memory to copy the data to
   * @param length The length of the data being copied into memory
   * @return the amount of gas consumed by the data copy operation
   */
  Gas dataCopyOperationGasCost(MessageFrame frame, UInt256 offset, UInt256 length);

  /**
   * Returns the cost of expanding memory for the specified access.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access occurs
   * @param length the length of the memory access
   * @return The gas required to expand memory for the specified access
   */
  Gas memoryExpansionGasCost(MessageFrame frame, UInt256 offset, UInt256 length);

  // Specific Non-call Operation Calculations

  /**
   * Returns the cost for executing a {@link BalanceOperation}.
   *
   * @return the cost for executing the balance operation
   */
  Gas getBalanceOperationGasCost();

  /**
   * Returns the cost for executing a {@link BlockHashOperation}.
   *
   * @return the cost for executing the block hash operation
   */
  Gas getBlockHashOperationGasCost();

  /**
   * Returns the cost for executing a {@link ExpOperation}.
   *
   * @param numBytes The number of bytes for the exponent parameter
   * @return the cost for executing the exp operation
   */
  Gas expOperationGasCost(int numBytes);

  /**
   * Returns the cost for executing a {@link ExtCodeCopyOperation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory to external code copy the data to
   * @param length The length of the code being copied into memory
   * @return the cost for executing the external code size operation
   */
  Gas extCodeCopyOperationGasCost(MessageFrame frame, UInt256 offset, UInt256 length);

  /**
   * Returns the cost for executing a {@link ExtCodeHashOperation}.
   *
   * @return the cost for executing the external code hash operation
   */
  Gas extCodeHashOperationGasCost();

  /**
   * Returns the cost for executing a {@link ExtCodeSizeOperation}.
   *
   * @return the cost for executing the external code size operation
   */
  Gas getExtCodeSizeOperationGasCost();

  /**
   * Returns the cost for executing a {@link JumpDestOperation}.
   *
   * @return the cost for executing the jump destination operation
   */
  Gas getJumpDestOperationGasCost();

  /**
   * Returns the cost for executing a {@link LogOperation}.
   *
   * @param frame The current frame
   * @param dataOffset The offset in memory where the log data exists
   * @param dataLength The length of the log data to read from memory
   * @param numTopics The number of topics in the log
   * @return the cost for executing the external code size operation
   */
  Gas logOperationGasCost(
      MessageFrame frame, UInt256 dataOffset, UInt256 dataLength, int numTopics);

  /**
   * Returns the cost for executing a {@link MLoadOperation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory load operation
   */
  Gas mLoadOperationGasCost(MessageFrame frame, UInt256 offset);

  /**
   * Returns the cost for executing a {@link MStoreOperation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory store operation
   */
  Gas mStoreOperationGasCost(MessageFrame frame, UInt256 offset);

  /**
   * Returns the cost for executing a {@link MStore8Operation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the access takes place
   * @return the cost for executing the memory byte store operation
   */
  Gas mStore8OperationGasCost(MessageFrame frame, UInt256 offset);

  /**
   * Returns the cost for executing a {@link SelfDestructOperation}.
   *
   * @param recipient The recipient of the self destructed inheritance (may be null)
   * @param inheritance The amount the recipient will receive
   * @return the cost for executing the self destruct operation
   */
  Gas selfDestructOperationGasCost(Account recipient, Wei inheritance);

  /**
   * Returns the cost for executing a {@link Sha3Operation}.
   *
   * @param frame The current frame
   * @param offset The offset in memory where the data to be hashed exists
   * @param length The hashed data length
   * @return the cost for executing the memory byte store operation
   */
  Gas sha3OperationGasCost(MessageFrame frame, UInt256 offset, UInt256 length);

  /**
   * Returns the cost for executing a {@link SLoadOperation}.
   *
   * @return the cost for executing the storage load operation
   */
  Gas getSloadOperationGasCost();

  /**
   * Returns the cost for an SSTORE operation.
   *
   * @param account the account that storage will be changed in
   * @param key the key the new value is to be stored under
   * @param newValue the new value to be stored
   * @return the gas cost for the SSTORE operation
   */
  Gas calculateStorageCost(Account account, UInt256 key, UInt256 newValue);

  /**
   * Returns the refund amount for an SSTORE operation.
   *
   * @param account the account that storage will be changed in
   * @param key the key the new value is to be stored under
   * @param newValue the new value to be stored
   * @return the gas refund for the SSTORE operation
   */
  Gas calculateStorageRefundAmount(Account account, UInt256 key, UInt256 newValue);

  /**
   * Returns the refund amount for deleting an account in a {@link SelfDestructOperation}.
   *
   * @return the refund amount for deleting an account in a self destruct operation
   */
  Gas getSelfDestructRefundAmount();

  /**
   * Returns the cost for executing a {@link BeginSubOperation}.
   *
   * @return the cost for executing begin sub operation
   */
  Gas getBeginSubGasCost();

  /**
   * Returns the cost of a SLOAD to a storage slot not previously loaded in the TX context.
   *
   * @return the cost of a SLOAD to a storage slot not previously loaded in the TX context.
   */
  default Gas getColdSloadCost() {
    return Gas.ZERO;
  }

  /**
   * Returns the cost to access an account not previously accessed in the TX context.
   *
   * @return the cost to access an account not previously accessed in the TX context.
   */
  default Gas getColdAccountAccessCost() {
    return Gas.ZERO;
  }

  /**
   * Returns the cost of a SLOAD to a storage slot that has previously been loaded in the TX
   * context.
   *
   * @return the cost of a SLOAD to a storage slot that has previously been loaded in the TX
   *     context.
   */
  default Gas getWarmStorageReadCost() {
    return Gas.ZERO;
  }

  /**
   * For the purposes of this gas calculator, is this address a precompile?
   *
   * @param address the address to test for being a precompile
   * @return true if it is a precompile.
   */
  default boolean isPrecompile(final Address address) {
    return false;
  }
}
