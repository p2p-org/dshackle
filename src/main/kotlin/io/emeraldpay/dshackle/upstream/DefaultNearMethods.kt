package io.emeraldpay.dshackle.upstream

import io.emeraldpay.dshackle.quorum.AlwaysQuorum
import io.emeraldpay.dshackle.quorum.BroadcastQuorum
import io.emeraldpay.dshackle.quorum.CallQuorum
import io.emeraldpay.dshackle.upstream.calls.CallMethods
import io.emeraldpay.dshackle.upstream.ethereum.rpc.RpcException

class DefaultNearMethods : CallMethods {

    private val all = setOf(
        "view_access_key",
        "query",
        "EXPERIMENTAL_changes",
        "block",
        "chunk",
        "EXPERIMENTAL_changes_in_block",
        "gas_price",
        "status",
        "network_info",
        "validators",
        "tx",
        "EXPERIMENTAL_tx_status",
        "EXPERIMENTAL_receipt",
    )

    private val add = setOf(
        "broadcast_tx_async",
        "broadcast_tx_commit",
    )

    private val allowedMethods: Set<String> = all + add

    override fun createQuorumFor(method: String): CallQuorum {
        return when {
            add.contains(method) -> BroadcastQuorum()
            all.contains(method) -> AlwaysQuorum()
            else -> AlwaysQuorum()
        }
    }

    override fun isCallable(method: String): Boolean {
        return allowedMethods.contains(method)
    }

    override fun isHardcoded(method: String): Boolean {
        return false
    }

    override fun executeHardcoded(method: String): ByteArray {
        throw RpcException(-32601, "Method not found")
    }

    override fun getGroupMethods(groupName: String): Set<String> =
        when (groupName) {
            "default" -> getSupportedMethods()
            else -> emptyList()
        }.toSet()

    override fun getSupportedMethods(): Set<String> {
        return allowedMethods.toSortedSet()
    }
}
