package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.MutableAccount;
import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Collection;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A contract creation message processor. */
public class MainnetContractCreationProcessor extends AbstractMessageProcessor {

  private static final Logger LOGGER = LogManager.getLogger();

  private final boolean requireCodeDepositToSucceed;

  private final GasCalculator gasCalculator;

  private final long initialContractNonce;

  private final int codeSizeLimit;

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final int codeSizeLimit,
      final long initialContractNonce,
      final Collection<Address> forceCommitAddresses) {
    super(evm, forceCommitAddresses);
    this.gasCalculator = gasCalculator;
    this.requireCodeDepositToSucceed = requireCodeDepositToSucceed;
    this.codeSizeLimit = codeSizeLimit;
    this.initialContractNonce = initialContractNonce;
  }

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final int codeSizeLimit,
      final long initialContractNonce) {
    this(
        gasCalculator,
        evm,
        requireCodeDepositToSucceed,
        codeSizeLimit,
        initialContractNonce,
        ImmutableSet.of());
  }

  private static boolean accountExists(final Account account) {
    // The account exists if it has sent a transaction
    // or already has its code initialized.
    return account.getNonce() > 0 || !account.getCode().isEmpty();
  }

  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public void start(final MessageFrame frame) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Executing contract-creation");
    }

    final MutableAccount sender = frame.getWorldState().getMutable(frame.getSenderAddress());
    sender.decrementBalance(frame.getValue());

    // TODO: Fix when tests are upstreamed or remove from test suit.
    // EIP-68 mandates that contract creations cannot collide any more.
    // While that EIP has been deferred, the General State reference tests
    // incorrectly include this even in early hard forks.
    final MutableAccount contract = frame.getWorldState().getOrCreate(frame.getContractAddress());
    if (accountExists(contract)) {
      LOGGER.trace(
          "Contract creation error: account as already been created for address {}",
          frame.getContractAddress());
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    } else {
      contract.incrementBalance(frame.getValue());
      contract.setNonce(initialContractNonce);
      frame.setState(MessageFrame.State.CODE_EXECUTING);
    }
  }

  @Override
  protected void codeSuccess(final MessageFrame frame) {
    final BytesValue contractCode = frame.getOutputData();

    final Gas depositFee = gasCalculator.codeDepositGasCost(contractCode.size());

    if (frame.getRemainingGas().compareTo(depositFee) < 0) {
      LOGGER.trace(
          "Not enough gas to pay the code deposit fee for {}: "
              + "remaining gas = {} < {} = deposit fee",
          frame.getContractAddress(),
          frame.getRemainingGas(),
          depositFee);
      if (requireCodeDepositToSucceed) {
        LOGGER.trace("Contract creation error: insufficient funds for code deposit");
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      } else {
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    } else {
      if (contractCode.size() > codeSizeLimit) {
        LOGGER.trace(
            "Contract creation error: code size {} exceeds code size limit {}",
            contractCode.size(),
            codeSizeLimit);
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      } else {
        frame.decrementRemainingGas(depositFee);

        // Finalize contract creation, setting the contract code.
        final MutableAccount contract =
            frame.getWorldState().getOrCreate(frame.getContractAddress());
        contract.setCode(contractCode);
        LOGGER.trace(
            "Successful creation of contract {} with code of size {} (Gas remaining: {})",
            frame.getContractAddress(),
            contractCode.size(),
            frame.getRemainingGas());
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    }
  }
}
