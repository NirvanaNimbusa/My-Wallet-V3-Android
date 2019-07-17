package piuk.blockchain.android.ui.thepit

import com.blockchain.annotations.CommonCode
import com.blockchain.kyc.models.nabu.NabuUser
import com.blockchain.nabu.models.NabuOfflineTokenResponse
import com.blockchain.android.testutils.rxInit
import com.blockchain.kyc.datamanagers.nabu.NabuDataManager
import com.blockchain.kyc.models.nabu.WalletMercuryLink
import com.blockchain.nabu.NabuToken
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Single
import junit.framework.Assert.assertEquals
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.thepit.PitLinking

@CommonCode("Also exists in kyc/test/TestHelper.kt")
private val validOfflineToken get() = NabuOfflineTokenResponse("userId", "lifetimeToken")

class PitPermissionsPresenterTest {

    @get:Rule
    val initSchedulers = rxInit {
        computationTrampoline()
        mainTrampoline()
    }

    private lateinit var presenter: PitPermissionsPresenter

    private val nabu: NabuDataManager = mock()
    private val nabuToken: NabuToken = mock()
    private val pitLinking: PitLinking = mock()
    private val view = mock<PitPermissionsView>()

    private val nabuUser: NabuUser = mock()

    @Before
    fun setUp() {
        whenever(nabuToken.fetchNabuToken()).thenReturn(Single.just(validOfflineToken))
        whenever(nabu.getUser(validOfflineToken)).thenReturn(Single.just(nabuUser))

        presenter = PitPermissionsPresenter(nabu, nabuToken, pitLinking).also {
            it.initView(view)
            it.onViewReady()
        }
    }

    @Test
    fun `when mail is not verified, view should prompt for verification`() {
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()
        verify(view).promptForEmailVerification(EMAIL_ADDRESS)
        verify(view).hideLoading()

        verify(view, never()).onLinkFailed(any())
        verify(view, never()).onLinkSuccess(any())
    }

    @Test
    fun `when mail is verified, there are no errors, the view should receive a formatted link - simple address `() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any())).thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            assertEquals(FORMATTED_LINK, firstValue)
        }
        verify(view).hideLoading()

        verify(view, never()).promptForEmailVerification(any())
        verify(view, never()).onLinkFailed(any())
    }

    @Test
    fun `when mail is verified, there are no errors, the view should receive a formatted link - extended address `() {
        // AKA check for URI encoding
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS_PLUS)

        whenever(nabu.linkWalletWithMercury(any())).thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            assertEquals(FORMATTED_LINK_PLUS, firstValue)
        }
        verify(view).hideLoading()

        verify(view, never()).promptForEmailVerification(any())
        verify(view, never()).onLinkFailed(any())
    }

    @Test
    fun `when the call to link fails an error should be reported`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any()))
            .thenReturn(Single.error(Throwable(LINK_ERROR)))

        presenter.tryToConnectWalletToPit()

        verify(view).showLoading()
        verify(view).hideLoading()
        verify(view).onLinkFailed(LINK_ERROR)

        verify(view, never()).onLinkSuccess(any())
        verify(view, never()).promptForEmailVerification(any())
        verify(view, never()).showEmailVerifiedDialog()
    }

    @Test
    fun `when the first call to link fails it should be possible to retry`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        whenever(nabu.linkWalletWithMercury(any()))
            .thenReturn(Single.error(Throwable(LINK_ERROR)))
            .thenReturn(Single.just(LINK_ID))

        presenter.tryToConnectWalletToPit() // Will fail
        presenter.tryToConnectWalletToPit() // Will succeed

        verify(view, times(2)).showLoading()
        verify(view, times(2)).hideLoading()

        verify(view).onLinkFailed(any())

        argumentCaptor<String>().apply {
            verify(view).onLinkSuccess(capture())
            assertEquals(FORMATTED_LINK, firstValue)
        }

        verify(view, never()).promptForEmailVerification(any())
    }

    @Test
    fun `verified check calls back into view if email is verified`() {
        whenever(nabuUser.emailVerified).thenReturn(true)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.checkEmailIsVerified()

        verify(view).showEmailVerifiedDialog()

        verify(view, never()).showLoading()
        verify(view, never()).hideLoading()
        verify(view, never()).promptForEmailVerification(any())
        verify(view, never()).onLinkSuccess(any())
    }

    @Test
    fun `no callback occurs if error is not verified and no error is raised`() {
        whenever(nabuUser.emailVerified).thenReturn(false)
        whenever(nabuUser.email).thenReturn(EMAIL_ADDRESS)

        presenter.checkEmailIsVerified()

        verify(view, never()).showEmailVerifiedDialog()
        verify(view, never()).onLinkSuccess(any())
        verify(view, never()).showLoading()
        verify(view, never()).hideLoading()
        verify(view, never()).promptForEmailVerification(any())
    }

    companion object {
        private val LINK_ID = WalletMercuryLink("0200000020")
        private const val EMAIL_ADDRESS = "test@test.com"
        private const val EMAIL_ADDRESS_PLUS = "test+test@test.com"
        private val FORMATTED_LINK = BuildConfig.PIT_URL + LINK_ID.linkId + "?email=test%40test.com"
        private val FORMATTED_LINK_PLUS = BuildConfig.PIT_URL + LINK_ID.linkId + "?email=test%2Btest%40test.com"
        private const val LINK_ERROR = "That went well"
    }
}
