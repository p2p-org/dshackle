package io.emeraldpay.dshackle.upstream.generic

import io.emeraldpay.dshackle.Chain
import io.emeraldpay.dshackle.config.ChainsConfig
import io.emeraldpay.dshackle.config.UpstreamsConfig
import io.emeraldpay.dshackle.config.UpstreamsConfig.Labels
import io.emeraldpay.dshackle.foundation.ChainOptions
import io.emeraldpay.dshackle.reader.JsonRpcReader
import io.emeraldpay.dshackle.startup.QuorumForLabels
import io.emeraldpay.dshackle.upstream.Capability
import io.emeraldpay.dshackle.upstream.DefaultUpstream
import io.emeraldpay.dshackle.upstream.Head
import io.emeraldpay.dshackle.upstream.Upstream
import io.emeraldpay.dshackle.upstream.UpstreamAvailability
import io.emeraldpay.dshackle.upstream.calls.CallMethods
import io.emeraldpay.dshackle.upstream.generic.connectors.ConnectorFactory
import io.emeraldpay.dshackle.upstream.generic.connectors.GenericConnector
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.Lifecycle
import reactor.core.Disposable
import java.util.concurrent.atomic.AtomicBoolean

class GenericUpstream(
    id: String,
    val chain: Chain,
    hash: Byte,
    options: ChainOptions.Options,
    role: UpstreamsConfig.UpstreamRole,
    targets: CallMethods?,
    private val node: QuorumForLabels.QuorumItem?,
    val chainConfig: ChainsConfig.ChainConfig,
    connectorFactory: ConnectorFactory,
    private val eventPublisher: ApplicationEventPublisher?,
) : DefaultUpstream(id, hash, null, UpstreamAvailability.OK, options, role, targets, node, chainConfig), Lifecycle {

    private val hasLiveSubscriptionHead: AtomicBoolean = AtomicBoolean(false)
    private val connector: GenericConnector = connectorFactory.create(this, chain, true)
    private var livenessSubscription: Disposable? = null
    override fun getHead(): Head {
        return connector.getHead()
    }

    override fun getIngressReader(): JsonRpcReader {
        return connector.getIngressReader()
    }

    override fun getLabels(): Collection<Labels> {
        return node?.let { listOf(it.labels) } ?: emptyList()
    }

    override fun getSubscriptionTopics(): List<String> {
        // should be implemented in next iterations
        // starknet doesn't have any subscriptions at all
        // polkadot serves subscriptions like separate json-rpc methods
        return emptyList()
    }

    // outdated, looks like applicable only for bitcoin and our ws_head trick
    override fun getCapabilities(): Set<Capability> {
        return if (hasLiveSubscriptionHead.get()) {
            setOf(Capability.RPC, Capability.BALANCE, Capability.WS_HEAD)
        } else {
            setOf(Capability.RPC, Capability.BALANCE)
        }
    }

    override fun isGrpc(): Boolean {
        // this implementation works only with statically configured upstreams
        return false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Upstream> cast(selfType: Class<T>): T {
        if (!selfType.isAssignableFrom(this.javaClass)) {
            throw ClassCastException("Cannot cast ${this.javaClass} to $selfType")
        }
        return this as T
    }

    override fun start() {
        log.info("Configured for ${chain.chainName}")
        connector.start()

//        livenessSubscription = connector.hasLiveSubscriptionHead().subscribe({
//            hasLiveSubscriptionHead.set(it)
//            eventPublisher?.publishEvent(UpstreamChangeEvent(chain, this, UPDATED))
//        }, {
//            log.debug("Error while checking live subscription for ${getId()}", it)
//        },)
    }

    override fun stop() {
        livenessSubscription?.dispose()
        livenessSubscription = null
        connector.stop()
    }

    override fun isRunning() = connector.isRunning()
}
