package li.biq.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiqliJsonTest {
    @Test
    fun decodesExactInstallReferrerResult() {
        val result = BiqliJson.decodeResult(
            """{
                "open":{"id":"biq_open_1","firstOpen":true,"matchedBy":"exact_install_referrer","confidence":"exact"},
                "click":{"id":"bq_click_1"},
                "link":{"id":"link_1","shortUrl":"https://go.example.com/hello","route":"/welcome"},
                "attribution":{"referralCode":"ALICE","metadata":{"campaign":"fall"},"dynamic":{}},
                "requestId":"req_1"
            }""".trimIndent(),
        )

        assertEquals(MatchType.EXACT_INSTALL_REFERRER, result.open.matchedBy)
        assertEquals("ALICE", result.attribution?.referralCode)
        assertEquals("fall", result.attribution?.metadata?.get("campaign"))
    }

    @Test
    fun decodesNoMatch() {
        val result = BiqliJson.decodeResult(
            """{
                "open":{"id":"biq_open_2","firstOpen":true,"matchedBy":"none","confidence":"none"},
                "click":null,"link":null,"attribution":null,"requestId":"req_2"
            }""".trimIndent(),
        )

        assertEquals(MatchType.NONE, result.open.matchedBy)
        assertNull(result.attribution)
    }

    @Test
    fun installReferrerExtractsOnlyValidBiqliToken() {
        val token = "bqmh_" + "a".repeat(43)
        assertEquals(
            token,
            BiqliInstallReferrer.extractToken("utm_source=test&biqli_token=$token"),
        )
        assertNull(BiqliInstallReferrer.extractToken("referral_code=ALICE"))
        assertNull(BiqliInstallReferrer.extractToken("biqli_token=bqmh_too-short"))
    }
}
