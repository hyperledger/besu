package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"revertReason"})
public class MixInIgnoreRevertReason {}
