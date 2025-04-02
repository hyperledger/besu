package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PayOperationTest {
  private static final Address SENDER_ADDRESS = Address.fromHexString("0xc0ff");
  private static final Address RECIPIENT_ADDRESS = Address.fromHexString("0xc0de");
  private static final EVM EOF_EVM = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
  private static final Code LEGACY_CODE =
    EOF_EVM.getCodeUncached(Bytes.wrap(Bytes.of(PayOperation.OPCODE), RECIPIENT_ADDRESS, Wei.ZERO));
  private static final Code SIMPLE_EOF =
    EOF_EVM.getCodeUncached(Bytes.fromHexString("0xEF00010100040200010001040000000080000000"));

  private SimpleWorld worldUpdater;

  @BeforeEach
  void beforeEach() {
    worldUpdater = new SimpleWorld();
    worldUpdater.createAccount(SENDER_ADDRESS, 0, Wei.ZERO);
    worldUpdater.createAccount(RECIPIENT_ADDRESS, 0, Wei.ZERO);
  }

  static Iterable<Arguments> data() {
    return List.of(
      Arguments.of(
        "not enough gas",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        99,
        100,
        ExceptionalHaltReason.INSUFFICIENT_GAS,
        RECIPIENT_ADDRESS,
        true),
      Arguments.of(
        "enough gas",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        5000,
        100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        true),
      Arguments.of(
        "sender == recipient",
        RECIPIENT_ADDRESS,
        RECIPIENT_ADDRESS,
        5000,
        100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        false),
      Arguments.of(
        "cold address",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        7300,
        2600,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        false),
      Arguments.of(
        "cold address - no account",
        SENDER_ADDRESS,
        Address.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a"),
        30000,
        27600,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        false));
  }

  @ParameterizedTest(name = "{index}: {0} {1}")
  @MethodSource("data")
  void noValueTest(
    final String name,
    final Address senderAddress,
    final Address recipientAddress,
    final long initialGas,
    final long chargedGas,
    final ExceptionalHaltReason haltReason,
    final Bytes stackItem,
    final boolean warmAddress) {
    final PayOperation operation = new PayOperation(new OsakaGasCalculator());

    final var frame =
      new TestMessageFrameBuilder()
        .sender(senderAddress)
        .initialGas(initialGas)
        .code(SIMPLE_EOF)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Wei.ZERO)
        .pushStackItem(recipientAddress)
        .worldUpdater(worldUpdater)
        .build();
    if (warmAddress) {
      frame.warmUpAddress(recipientAddress);
    }

    var result = operation.execute(frame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    assertThat(frame.getStackItem(0)).isEqualTo(stackItem);

    Account recipientAccount = worldUpdater.get(recipientAddress);
    if (recipientAccount != null) {
      assertThat(recipientAccount.getBalance()).isEqualTo(Wei.ZERO);
    }
  }

  static Iterable<Arguments> valueData() {
    return List.of(
      Arguments.of(
        "not enough gas",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        5000,
        9100,
        ExceptionalHaltReason.INSUFFICIENT_GAS,
        RECIPIENT_ADDRESS,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        true),
      Arguments.of(
        "enough value",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        10000,
        9100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        Wei.of(2000),
        true),
      Arguments.of(
        "not enough value",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        40000,
        9100,
        null,
        AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM,
        Wei.of(1000),
        Wei.of(500),
        Wei.of(500),
        Wei.of(0),
        Wei.of(0),
        true),
      Arguments.of(
        "sender == recipient",
        RECIPIENT_ADDRESS,
        RECIPIENT_ADDRESS,
        10000,
        9100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        true),
      Arguments.of(
        "cold address",
        SENDER_ADDRESS,
        RECIPIENT_ADDRESS,
        12000,
        11600,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        Wei.of(2000),
        false),
      Arguments.of(
        "cold address new account",
        SENDER_ADDRESS,
        Address.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a"),
        40000,
        36600,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(0),
        Wei.of(1000),
        false),
      Arguments.of(
        "cold address precompile",
        SENDER_ADDRESS,
        Address.fromHexString("0x01"),
        10000,
        9100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(0),
        Wei.of(1000),
        false));
  }

  @ParameterizedTest(name = "{index}: {0} {1}")
  @MethodSource("valueData")
  void valueTest(
    final String name,
    final Address senderAddress,
    final Address recipientAddress,
    final long initialGas,
    final long chargedGas,
    final ExceptionalHaltReason haltReason,
    final Bytes stackItem,
    final Wei valueSent,
    final Wei initialSenderBalance,
    final Wei senderBalance,
    final Wei initialRecipientBalance,
    final Wei recipientBalance,
    final boolean warmAddress) {
    final PayOperation operation = new PayOperation(new OsakaGasCalculator());

    final var frame =
      new TestMessageFrameBuilder()
        .sender(senderAddress)
        .initialGas(initialGas)
        .code(SIMPLE_EOF)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(valueSent)
        .pushStackItem(recipientAddress)
        .worldUpdater(worldUpdater)
        .build();
    if (warmAddress) {
      frame.warmUpAddress(recipientAddress);
    }

    MutableAccount senderAccount = worldUpdater.getAccount(senderAddress);
    senderAccount.setBalance(initialSenderBalance);
    MutableAccount recipientAccount = worldUpdater.getAccount(recipientAddress);
    if (recipientAccount != null) {
      recipientAccount.setBalance(initialRecipientBalance);
    }

    var result = operation.execute(frame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    assertThat(frame.getStackItem(0)).isEqualTo(stackItem);

    assertThat(senderAccount.getBalance()).isEqualTo(senderBalance);
    assertThat(worldUpdater.getAccount(recipientAddress).getBalance()).isEqualTo(recipientBalance);
  }

  static List<Arguments> staticContext() {
    return List.of(
      Arguments.of(
        "no value in static context",
        SENDER_ADDRESS,
        101,
        100,
        ExceptionalHaltReason.ILLEGAL_STATE_CHANGE,
        RECIPIENT_ADDRESS,
        Wei.of(0),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        true),
      Arguments.of(
        "sender == recipient static context",
        RECIPIENT_ADDRESS,
        10000,
        9100,
        ExceptionalHaltReason.ILLEGAL_STATE_CHANGE,
        RECIPIENT_ADDRESS,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(2000),
        true),
      Arguments.of(
        "value in static context",
        SENDER_ADDRESS,
        9200,
        9100,
        ExceptionalHaltReason.ILLEGAL_STATE_CHANGE,
        RECIPIENT_ADDRESS,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        true),
      Arguments.of(
        "no gas in static context",
        SENDER_ADDRESS,
        9000,
        9100,
        ExceptionalHaltReason.ILLEGAL_STATE_CHANGE,
        RECIPIENT_ADDRESS,
        Wei.of(1),
        Wei.of(2000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        true),
      Arguments.of(
        "value in non-static context",
        SENDER_ADDRESS,
        9101,
        9100,
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        Wei.of(1000),
        Wei.of(2000),
        Wei.of(1000),
        Wei.of(1000),
        Wei.of(2000),
        false));
  }

  @ParameterizedTest
  @MethodSource("staticContext")
  void staticCallContext(
    final String name,
    final Address senderAddress,
    final long initialGas,
    final long chargedGas,
    final ExceptionalHaltReason haltReason,
    final Bytes stackItem,
    final Wei valueSent,
    final Wei initialSenderBalance,
    final Wei senderBalance,
    final Wei initialRecipientBalance,
    final Wei recipientBalance,
    final boolean isStatic) {

    final PayOperation operation = new PayOperation(new OsakaGasCalculator());

    final var frame =
      new TestMessageFrameBuilder()
        .sender(senderAddress)
        .initialGas(initialGas)
        .code(SIMPLE_EOF)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(valueSent)
        .pushStackItem(RECIPIENT_ADDRESS)
        .worldUpdater(worldUpdater)
        .isStatic(isStatic)
        .build();

    //TODO: add extra cold account case
    frame.warmUpAddress(RECIPIENT_ADDRESS);
    MutableAccount senderAccount = worldUpdater.getAccount(SENDER_ADDRESS);
    senderAccount.setBalance(initialSenderBalance);
    MutableAccount recipientAccount = worldUpdater.getAccount(RECIPIENT_ADDRESS);
    recipientAccount.setBalance(initialRecipientBalance);

    var result = operation.execute(frame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    assertThat(frame.getStackItem(0)).isEqualTo(stackItem);

    assertThat(senderAccount.getBalance()).isEqualTo(senderBalance);
    assertThat(recipientAccount.getBalance()).isEqualTo(recipientBalance);
  }

  static List<Arguments> bigAddress() {
    return List.of(
      Arguments.of(
        "2 byte address",
        Bytes.fromHexString("0xd66a"),
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        36600L),
      Arguments.of(
        "exact 20 byte address",
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a"),
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        36600L),
      Arguments.of(
        "21 byte address with leading zero",
        Bytes.fromHexString("0x00341a2e456a2c23ca9a8c7d765521bcee2188d66a"),
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        36600L),
      Arguments.of(
        "exact 21 bytes address",
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a76"),
        ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE,
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a76"),
        0),
      Arguments.of(
        "20 bytes address padded to 32 bytes",
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a", 32),
        null,
        AbstractExtCallOperation.EOF1_SUCCESS_STACK_ITEM,
        36600L),
      Arguments.of(
        "22 byte address size padded to 32 bytes",
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a76ad", 32),
        ExceptionalHaltReason.ADDRESS_OUT_OF_RANGE,
        Bytes.fromHexString("0x341a2e456a2c23ca9a8c7d765521bcee2188d66a76ad", 32),
        0));
  }

  @ParameterizedTest
  @MethodSource("bigAddress")
  void tooBigAddress(
    final String name,
    final Bytes recipient,
    final ExceptionalHaltReason haltReason,
    final Bytes stackItem,
    final long gasCost) {
    final PayOperation operation = new PayOperation(new OsakaGasCalculator());

    final var frame =
      new TestMessageFrameBuilder()
        .sender(SENDER_ADDRESS)
        .initialGas(Long.MAX_VALUE)
        .code(SIMPLE_EOF)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Wei.of(1000))
        .pushStackItem(recipient)
        .worldUpdater(worldUpdater)
        .build();

    MutableAccount senderAccount = worldUpdater.getAccount(SENDER_ADDRESS);
    senderAccount.setBalance(Wei.of(2000));

    var result = operation.execute(frame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(gasCost);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    assertThat(frame.getStackItem(0)).isEqualTo(stackItem);
  }

  @Test
  void legacyTest() {
    final PayOperation operation = new PayOperation(new OsakaGasCalculator());

    final var messageFrame =
      new TestMessageFrameBuilder()
        .initialGas(400000)
        .code(LEGACY_CODE)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Bytes.EMPTY)
        .pushStackItem(Wei.ZERO)
        .pushStackItem(RECIPIENT_ADDRESS)
        .worldUpdater(worldUpdater)
        .build();
    messageFrame.warmUpAddress(RECIPIENT_ADDRESS);
    worldUpdater.getAccount(RECIPIENT_ADDRESS).setCode(SIMPLE_EOF.getBytes());

    var result = operation.execute(messageFrame, EOF_EVM);

    assertThat(result.getGasCost()).isZero();
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
  }
}
