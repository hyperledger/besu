package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_ABSENT)
public interface JsonRpcResult {}
