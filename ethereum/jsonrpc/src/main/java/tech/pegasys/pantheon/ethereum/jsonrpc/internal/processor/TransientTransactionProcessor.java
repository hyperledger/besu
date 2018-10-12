package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

/*
 * Used to process transactions for eth_call and eth_estimateGas.
 *
 * The processing won't affect the world state, it is used to execute read operations on the
 * blockchain or to estimate the transaction gas cost.
 */
public class TransientTransactionProcessor {

  // Dummy signature for transactions to not fail being processed.
  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  // TODO: Identify a better default from account to use, such as the registered
  // coinbase or an account currently unlocked by the client.
  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule<?> protocolSchedule;

  public TransientTransactionProcessor(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule<?> protocolSchedule) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
  }

  public Optional<TransientTransactionProcessingResult> process(
      final CallParameter callParams, final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).orElse(null);
    if (header == null) {
      return Optional.empty();
    }
    final MutableWorldState worldState = worldStateArchive.getMutable(header.getStateRoot());

    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;
    final Account sender = worldState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;
    final long gasLimit =
        callParams.getGasLimit() >= 0 ? callParams.getGasLimit() : header.getGasLimit();
    final Wei gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final BytesValue payload =
        callParams.getPayload() != null ? callParams.getPayload() : BytesValue.EMPTY;

    final Transaction transaction =
        Transaction.builder()
            .nonce(nonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .to(callParams.getTo())
            .sender(senderAddress)
            .value(value)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .build();

    final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());

    final TransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockNumber(header.getNumber()).getTransactionProcessor();
    final TransactionProcessor.Result result =
        transactionProcessor.processTransaction(
            blockchain,
            worldState.updater(),
            header,
            transaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            new BlockHashLookup(header, blockchain));

    return Optional.of(new TransientTransactionProcessingResult(transaction, result));
  }
}
