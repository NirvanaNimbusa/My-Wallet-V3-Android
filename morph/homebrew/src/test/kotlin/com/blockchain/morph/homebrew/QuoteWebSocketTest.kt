package com.blockchain.morph.homebrew

import com.blockchain.koin.modules.homeBrewModule
import com.blockchain.morph.exchange.mvi.Quote
import com.blockchain.morph.quote.ExchangeQuoteRequest
import com.blockchain.network.modules.MoshiBuilderInterceptorList
import com.blockchain.network.modules.apiModule
import com.blockchain.network.websocket.WebSocket
import com.blockchain.testutils.bitcoin
import com.blockchain.testutils.ether
import com.blockchain.testutils.getStringFromResource
import com.blockchain.testutils.usd
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import info.blockchain.balance.CryptoCurrency
import io.reactivex.subjects.PublishSubject
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should equal`
import org.junit.Before
import org.junit.Test
import org.koin.dsl.module.applicationContext
import org.koin.standalone.StandAloneContext
import org.koin.standalone.get
import org.koin.test.AutoCloseKoinTest

class QuoteWebSocketTest : AutoCloseKoinTest() {

    @Before
    fun startKoin() {
        StandAloneContext.startKoin(
            listOf(
                homeBrewModule,
                applicationContext {
                    bean {
                        MoshiBuilderInterceptorList(
                            listOf(
                                get("homeBrew")
                            )
                        )
                    }
                },
                apiModule
            )
        )
    }

    @Test
    fun `sends a request down the socket`() {
        val actualSocket =
            mock<WebSocket<String, String>>()

        givenAWebSocket(actualSocket)
            .subscribe(
                ExchangeQuoteRequest.Selling(
                    offering = 100.0.bitcoin(),
                    wanted = CryptoCurrency.ETHER,
                    indicativeFiatSymbol = "USD"
                )
            )

        verify(actualSocket).send(
            "{\"channel\":\"conversion\"," +
                "\"operation\":\"subscribe\"," +
                "\"params\":{" +
                "\"fiatCurrency\":\"USD\"," +
                "\"fix\":\"base\"," +
                "\"pair\":\"BTC-ETH\"," +
                "\"type\":\"conversionSpecification\"," +
                "\"volume\":\"100.0\"}" +
                "}"
        )

        verifyNoMoreInteractions(actualSocket)
    }

    @Test
    fun `when you subscribe to the same parameters, just one request goes down socket`() {
        val actualSocket =
            mock<WebSocket<String, String>>()

        givenAWebSocket(actualSocket)
            .apply {
                subscribe(
                    ExchangeQuoteRequest.Selling(
                        offering = 100.0.bitcoin(),
                        wanted = CryptoCurrency.ETHER,
                        indicativeFiatSymbol = "USD"
                    )
                )
                subscribe(
                    ExchangeQuoteRequest.Selling(
                        offering = 100.0.bitcoin(),
                        wanted = CryptoCurrency.ETHER,
                        indicativeFiatSymbol = "USD"
                    )
                )
            }

        verify(actualSocket).send(any())
        verifyNoMoreInteractions(actualSocket)
    }

    @Test
    fun `when you change the subscription, an unsubscribe happens`() {
        val actualSocket =
            mock<WebSocket<String, String>>()

        givenAWebSocket(actualSocket)
            .apply {
                subscribe(
                    ExchangeQuoteRequest.Selling(
                        offering = 200.0.bitcoin(),
                        wanted = CryptoCurrency.ETHER,
                        indicativeFiatSymbol = "USD"
                    )
                )
                subscribe(
                    ExchangeQuoteRequest.Selling(
                        offering = 300.0.bitcoin(),
                        wanted = CryptoCurrency.ETHER,
                        indicativeFiatSymbol = "USD"
                    )
                )
            }

        verify(actualSocket).send(
            "{\"channel\":\"conversion\"," +
                "\"operation\":\"subscribe\"," +
                "\"params\":{" +
                "\"fiatCurrency\":\"USD\"," +
                "\"fix\":\"base\"," +
                "\"pair\":\"BTC-ETH\"," +
                "\"type\":\"conversionSpecification\"," +
                "\"volume\":\"200.0\"}" +
                "}"
        )

        verify(actualSocket).send(
            "{\"channel\":\"conversion\"," +
                "\"operation\":\"unsubscribe\"," +
                "\"params\":{" +
                "\"pair\":\"BTC-ETH\"," +
                "\"type\":\"conversionPair\"}" +
                "}"
        )

        verify(actualSocket).send(
            "{\"channel\":\"conversion\"," +
                "\"operation\":\"subscribe\"," +
                "\"params\":{" +
                "\"fiatCurrency\":\"USD\"," +
                "\"fix\":\"base\"," +
                "\"pair\":\"BTC-ETH\"," +
                "\"type\":\"conversionSpecification\"," +
                "\"volume\":\"300.0\"}" +
                "}"
        )
    }

    @Test
    fun `when the socket responds with a message it is converted from json to incoming type`() {
        val subject = PublishSubject.create<String>()
        val actualSocket =
            mock<WebSocket<String, String>> {
                on { responses } `it returns` subject
            }

        val test = givenAWebSocket(actualSocket)
            .quotes
            .test()

        subject.onNext(getStringFromResource("quotes/quote_receive.json"))

        test.values().single() `should equal` Quote(
            from = Quote.Value(0.15.bitcoin(), 96.77.usd()),
            to = Quote.Value(0.27.ether(), 100.0.usd())
        )
    }

    @Test
    fun `when the socket responds with a subscribed message, don't pass it on`() {
        val subject = PublishSubject.create<String>()
        val actualSocket =
            mock<WebSocket<String, String>> {
                on { responses } `it returns` subject
            }

        val test = givenAWebSocket(actualSocket)
            .quotes
            .test()

        subject.onNext(getStringFromResource("quotes/quote_subscription_confirmation.json"))

        test.values() `should equal` emptyList()
    }

    private fun givenAWebSocket(actualSocket: WebSocket<String, String>): QuoteService =
        QuoteWebSocket(actualSocket, get())
}