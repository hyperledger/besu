package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class WithdrawalsProcessorTest {

  private final WithdrawalsProcessor.DefaultWithdrawalsProcessor defaultWithdrawalsProcessor =
      new WithdrawalsProcessor.DefaultWithdrawalsProcessor();

  @Test
  void noopProcessor_shouldNotProcessWithdrawals() {
    final MutableWorldState worldState =
        createWorldStateWithAccounts(List.of(entry("0x1", 1), entry("0x2", 2), entry("0x3", 3)));
    final MutableWorldState originalState = worldState.copy();
    final WorldUpdater updater = worldState.updater();

    final List<Withdrawal> withdrawals =
        List.of(
            new Withdrawal(
                UInt64.valueOf(100),
                UInt64.valueOf(1000),
                Address.fromHexString("0x1"),
                Wei.of(100)),
            new Withdrawal(
                UInt64.valueOf(200),
                UInt64.valueOf(2000),
                Address.fromHexString("0x2"),
                Wei.of(200)));
    final WithdrawalsProcessor noopWithdrawalsProcessor =
        new WithdrawalsProcessor.NoOpWithdrawalsProcessor();
    noopWithdrawalsProcessor.processWithdrawals(withdrawals, updater);

    assertThat(worldState.get(Address.fromHexString("0x1")).getBalance()).isEqualTo(Wei.of(1));
    assertThat(worldState.get(Address.fromHexString("0x2")).getBalance()).isEqualTo(Wei.of(2));
    assertThat(worldState.get(Address.fromHexString("0x3")).getBalance()).isEqualTo(Wei.of(3));
    assertThat(originalState).isEqualTo(worldState);
  }

  @Test
  void defaultProcessor_shouldProcessEmptyWithdrawalsWithoutChangingWorldState() {
    final MutableWorldState worldState =
        createWorldStateWithAccounts(List.of(entry("0x1", 1), entry("0x2", 2), entry("0x3", 3)));
    final MutableWorldState originalState = worldState.copy();
    final WorldUpdater updater = worldState.updater();

    defaultWithdrawalsProcessor.processWithdrawals(Collections.emptyList(), updater);

    assertThat(worldState.get(Address.fromHexString("0x1")).getBalance()).isEqualTo(Wei.of(1));
    assertThat(worldState.get(Address.fromHexString("0x2")).getBalance()).isEqualTo(Wei.of(2));
    assertThat(worldState.get(Address.fromHexString("0x3")).getBalance()).isEqualTo(Wei.of(3));
    assertThat(originalState).isEqualTo(worldState);
  }

  @Test
  void defaultProcessor_shouldProcessWithdrawalsUpdatingExistingAccountsBalance() {
    final MutableWorldState worldState =
        createWorldStateWithAccounts(List.of(entry("0x1", 1), entry("0x2", 2), entry("0x3", 3)));
    final WorldUpdater updater = worldState.updater();

    final List<Withdrawal> withdrawals =
        List.of(
            new Withdrawal(
                UInt64.valueOf(100),
                UInt64.valueOf(1000),
                Address.fromHexString("0x1"),
                Wei.of(100)),
            new Withdrawal(
                UInt64.valueOf(200),
                UInt64.valueOf(2000),
                Address.fromHexString("0x2"),
                Wei.of(200)));
    defaultWithdrawalsProcessor.processWithdrawals(withdrawals, updater);

    assertThat(worldState.get(Address.fromHexString("0x1")).getBalance()).isEqualTo(Wei.of(101));
    assertThat(worldState.get(Address.fromHexString("0x2")).getBalance()).isEqualTo(Wei.of(202));
    assertThat(worldState.get(Address.fromHexString("0x3")).getBalance()).isEqualTo(Wei.of(3));
  }

  @Test
  void defaultProcessor_shouldProcessWithdrawalsUpdatingEmptyAccountsBalance() {
    final MutableWorldState worldState = createWorldStateWithAccounts(Collections.emptyList());
    final WorldUpdater updater = worldState.updater();

    final List<Withdrawal> withdrawals =
        List.of(
            new Withdrawal(
                UInt64.valueOf(100),
                UInt64.valueOf(1000),
                Address.fromHexString("0x1"),
                Wei.of(100)),
            new Withdrawal(
                UInt64.valueOf(200),
                UInt64.valueOf(2000),
                Address.fromHexString("0x2"),
                Wei.of(200)));
    defaultWithdrawalsProcessor.processWithdrawals(withdrawals, updater);

    assertThat(worldState.get(Address.fromHexString("0x1")).getBalance()).isEqualTo(Wei.of(100));
    assertThat(worldState.get(Address.fromHexString("0x2")).getBalance()).isEqualTo(Wei.of(200));
  }

  private MutableWorldState createWorldStateWithAccounts(
      final List<Map.Entry<String, Integer>> accountBalances) {
    final MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();
    final WorldUpdater updater = worldState.updater();
    accountBalances.forEach(
        entry ->
            updater.createAccount(
                Address.fromHexString(entry.getKey()), 1, Wei.of(entry.getValue())));
    updater.commit();
    return worldState;
  }
}
