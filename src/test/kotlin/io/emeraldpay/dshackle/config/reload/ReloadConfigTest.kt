package io.emeraldpay.dshackle.config.reload

import io.emeraldpay.dshackle.Chain.ETHEREUM__MAINNET
import io.emeraldpay.dshackle.Chain.POLYGON__MAINNET
import io.emeraldpay.dshackle.Config
import io.emeraldpay.dshackle.FileResolver
import io.emeraldpay.dshackle.config.MainConfig
import io.emeraldpay.dshackle.config.ReloadConfiguration.Companion.default
import io.emeraldpay.dshackle.config.UpstreamsConfig
import io.emeraldpay.dshackle.config.UpstreamsConfigReader
import io.emeraldpay.dshackle.foundation.ChainOptionsReader
import io.emeraldpay.dshackle.startup.ConfiguredUpstreams
import io.emeraldpay.dshackle.startup.UpstreamChangeEvent
import io.emeraldpay.dshackle.upstream.CurrentMultistreamHolder
import io.emeraldpay.dshackle.upstream.Multistream
import io.emeraldpay.dshackle.upstream.Upstream
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.util.ResourceUtils
import java.io.File


class ReloadConfigTest {
    private val fileResolver = FileResolver(File(""))
    private val mainConfig = MainConfig()

    private val optionsReader = ChainOptionsReader()
    private val upstreamsConfigReader = UpstreamsConfigReader(fileResolver, optionsReader)

    private val request = HttpPost("http://localhost:8093/reload")
    private val client = HttpClientBuilder.create().build()

    @BeforeEach
    fun setupTests() {
        mainConfig.upstreams = null
    }

    @Test
    fun `reload upstreams changes`() {
        val up1 = upstream("local1")
        val up2 = upstream("local2")
        val up3 = upstream("local3")

        val msEth = mock<Multistream> {
            on { getAll() } doReturn listOf(up1, up2)
        }
        val msPoly = mock<Multistream> {
            on { getAll() } doReturn listOf(up3)
        }

        val newConfigFile = ResourceUtils.getFile("classpath:configs/upstreams-changed.yaml")
        val config = mock<Config> {
            on { getConfigPath() } doReturn newConfigFile
        }
        val reloadConfigService = ReloadConfigService(config, fileResolver, mainConfig)
        val applicationEventPublisher = mock<ApplicationEventPublisher>()
        val currentMultistreamHolder = mock<CurrentMultistreamHolder> {
            on { getUpstream(ETHEREUM__MAINNET) } doReturn msEth
            on { getUpstream(POLYGON__MAINNET) } doReturn msPoly
        }
        val configuredUpstreams = mock<ConfiguredUpstreams>()
        val reloadConfigUpstreamService = ReloadConfigUpstreamService(
            applicationEventPublisher, currentMultistreamHolder, configuredUpstreams,
        )
        val reloadConfig = ReloadConfigSetup(reloadConfigService, reloadConfigUpstreamService, default())
        reloadConfig.start()

        val initialConfigIs = ResourceUtils.getFile("classpath:configs/upstreams-initial.yaml").inputStream()
        val initialConfig = upstreamsConfigReader.read(initialConfigIs)!!
        val newConfig = upstreamsConfigReader.read(newConfigFile.inputStream())!!
        mainConfig.upstreams = initialConfig

        client.execute(request)

        val captor = ArgumentCaptor.forClass(UpstreamChangeEvent::class.java);
        verify(applicationEventPublisher, times(2)).publishEvent(captor.capture())
        verify(configuredUpstreams).processUpstreams(
            UpstreamsConfig(
                newConfig.defaultOptions,
                mutableListOf(newConfig.upstreams[0], newConfig.upstreams[2])
            )
        )

        assertEquals(3, mainConfig.upstreams!!.upstreams.size)
        assertEquals(newConfig, mainConfig.upstreams)
        assertEquals(
            UpstreamChangeEvent(ETHEREUM__MAINNET, up1, UpstreamChangeEvent.ChangeType.REMOVED),
            captor.allValues[0]
        )
        assertEquals(
            UpstreamChangeEvent(POLYGON__MAINNET, up3, UpstreamChangeEvent.ChangeType.REMOVED),
            captor.allValues[1]
        )

        reloadConfig.stop()
    }

    private fun upstream(id: String): Upstream =
        mock {
            on { getId() } doReturn id
        }
}
