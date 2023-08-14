package io.emeraldpay.dshackle.upstream.ethereum

import io.emeraldpay.dshackle.upstream.Head
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import java.time.Duration

class HeadLivenessValidator(
    val head: Head,
    val expectedBlockTime: Duration,
    val scheduler: Scheduler,
    private val upstreamId: String
) {
    companion object {
        const val CHECKED_BLOCKS_UNTIL_LIVE = 3
        private val log = LoggerFactory.getLogger(HeadLivenessValidator::class.java)
    }

    private fun fallback(): Flux<Boolean> {
        return Flux.defer {
            log.info("head liveness check broken with timeout in $upstreamId")
            Flux.just(false).concatWith(getFlux()) // emit false and then restart the Flux
        }
    }

    fun getFlux(): Flux<Boolean> {
        // first we have moving window of 2 blocks and check that they are consecutive ones
        return head.getFlux().buffer(2, 1).map {
            it.last().height - it.first().height == 1L
        }.scan(Pair(0, true)) { acc, value ->
            // then we accumulate consecutive true events, false resets counter
            if (value) {
                Pair(acc.first + 1, true)
            } else {
                log.info("non consecutive blocks in head for $upstreamId")
                Pair(0, false)
            }
        }.flatMap { (count, value) ->
            // we emit when we have false or checked CHECKED_BLOCKS_UNTIL_LIVE blocks
            // CHECKED_BLOCKS_UNTIL_LIVE blocks == (CHECKED_BLOCKS_UNTIL_LIVE - 1) consecutive true
            when {
                count >= (CHECKED_BLOCKS_UNTIL_LIVE - 1) -> Flux.just(true)
                !value -> Flux.just(false)
                else -> Flux.empty()
            }
            // finally, we timeout after we waited for double the time we needed to emit those blocks
        }.timeout(
            expectedBlockTime.multipliedBy(CHECKED_BLOCKS_UNTIL_LIVE.toLong() * 2),
            fallback()
        ).subscribeOn(scheduler)
    }
}
