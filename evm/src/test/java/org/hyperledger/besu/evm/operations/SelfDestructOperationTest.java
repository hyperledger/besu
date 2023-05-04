package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SelfDestructEIP6780Operation;
import org.hyperledger.besu.evm.operation.SelfDestructFrontierOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayDeque;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SelfDestructOperationTest {

  private static final Bytes SELFDESTRUCT_CODE =
      Bytes.fromHexString(
          "6000" // PUSH1 0
              + "35" // CALLDATALOAD
              + "ff" // SELFDESTRUCT
          );

  private MessageFrame messageFrame;
  @Mock private WorldUpdater worldUpdater;
  @Mock private WrappedEvmAccount account;
  @Mock private MutableAccount mutableAccount;
  @Mock private EVM evm;
  @Mock private MutableAccount newMutableAccount;

  private final SelfDestructFrontierOperation operation =
      new SelfDestructFrontierOperation(new ConstantinopleGasCalculator());

  private final SelfDestructEIP6780Operation newOperation =
      new SelfDestructEIP6780Operation(new ConstantinopleGasCalculator());

  public void setUp(final String contract, final String beneficiary, final String balanceHex) {

    when(account.getMutable()).thenReturn(mutableAccount);
    Address contractAddress = Address.fromHexString(contract);
    Address beneficiaryAddress = Address.fromHexString(beneficiary);
    messageFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .contract(Address.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(beneficiaryAddress)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(CodeFactory.createCode(SELFDESTRUCT_CODE, 0, true))
            .depth(1)
            .completer(__ -> {})
            .address(contractAddress)
            .blockHashLookup(n -> Hash.hash(Words.longBytes(n)))
            .blockValues(mock(BlockValues.class))
            .gasPrice(Wei.ZERO)
            .messageFrameStack(new ArrayDeque<>())
            .miningBeneficiary(Address.ZERO)
            .originator(Address.ZERO)
            .initialGas(100_000L)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.pushStackItem(Bytes.fromHexString(beneficiary));

    when(mutableAccount.getBalance()).thenReturn(Wei.fromHexString(balanceHex));
    when(mutableAccount.getAddress()).thenReturn(contractAddress, beneficiaryAddress);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(account);
  }

  public static Object[][] params() {
    return new Object[][] {
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x1234567890abcdef1234567890abcdef12345678",
        true,
        "0x1234567890"
      },
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x1234567890abcdef1234567890abcdef12345678",
        false,
        "0x1234567890"
      },
      {
        "0x00112233445566778899aabbccddeeff11223344",
        "0x00112233445566778899aabbccddeeff11223344",
        true,
        "0x1234567890"
      },
      {
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x1234567890abcdef1234567890abcdef12345678",
        false,
        "0x1234567890"
      },
    };
  }

  @ParameterizedTest
  @MethodSource("params")
  void checkContractDeletionFrontier(
      final String contract,
      final String beneficiary,
      final boolean ignoredNewAccount,
      final String balanceHex) {

    setUp(contract, beneficiary, balanceHex);
    final Operation.OperationResult operationResult = operation.execute(messageFrame, evm);
    assertThat(operationResult).isNotNull();
    assertThat(messageFrame.getSelfDestructs()).contains(Address.fromHexString(contract));

    verify(account).getBalance();
    verify(account).isEmpty();
    if (!contract.equals(beneficiary)) {
      verify(mutableAccount).incrementBalance(Wei.fromHexString(balanceHex));
    }
    verify(mutableAccount).setBalance(Wei.ZERO);

    verifyNoMoreInteractions(worldUpdater, account, mutableAccount, evm, newMutableAccount);
  }

  @ParameterizedTest
  @MethodSource("params")
  void checkContractDeletionEIP6780(
      final String contract,
      final String beneficiary,
      final boolean newAccount,
      final String balanceHex) {

    setUp(contract, beneficiary, balanceHex);
    when(mutableAccount.isNewAccount()).thenReturn(newAccount);
    final Operation.OperationResult operationResult = newOperation.execute(messageFrame, evm);
    assertThat(operationResult).isNotNull();
    if (newAccount) {
      assertThat(messageFrame.getSelfDestructs()).contains(Address.fromHexString(contract));
    } else {
      assertThat(messageFrame.getSelfDestructs()).isEmpty();
    }

    verify(account).getBalance();
    verify(account).isEmpty();
    if (!contract.equals(beneficiary)) {
      verify(mutableAccount).incrementBalance(Wei.fromHexString(balanceHex));
    }
    verify(mutableAccount).setBalance(Wei.ZERO);

    verifyNoMoreInteractions(worldUpdater, account, mutableAccount, evm, newMutableAccount);
  }
}
