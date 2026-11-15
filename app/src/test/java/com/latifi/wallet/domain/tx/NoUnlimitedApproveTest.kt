package com.latifi.wallet.domain.tx

import com.latifi.wallet.guard.ResourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A source scan, so no later change can add an unlimited approval (T-05-12-1). The main sources
 * must hold no 64 digit `f` value and no approve call whose amount is not the named exact amount.
 */
class NoUnlimitedApproveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun mainSourcesHaveNoMaximumValueAndNoApprovalOfAnUnnamedAmount() {
        val findings = scan(ResourceScan.SOURCE_ROOT)

        assertTrue("Unlimited or unnamed approvals:\n" + findings.joinToString("\n"), findings.isEmpty())
    }

    @Test
    fun theScanFlagsAMaximumValueAndAnUnnamedAmount() {
        val dir = tmp.newFolder("fixture")
        File(dir, "Bad.kt").writeText(
            """
            package sample
            val max = "0x${"f".repeat(64)}"
            fun a() = Erc20Abi.approve(spender, MAX_UINT)
            fun b() = Erc20Abi.approveLoanBook(BigInteger.ONE.shiftLeft(256))
            fun ok(needed: BigInteger) = Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, needed)
            """.trimIndent(),
        )

        val findings = scan(dir)

        assertEquals(findings.toString(), 3, findings.size)
    }

    private fun scan(root: File): List<String> {
        val found = mutableListOf<String>()
        for (file in ResourceScan.kotlinFiles(root)) {
            val text = file.readText()
            if (MAX_VALUE.containsMatchIn(text)) found += "${file.name}: a maximum value literal"
            for (call in APPROVE_CALL.findAll(text)) {
                val amount = call.groupValues[1].substringAfterLast(',').trim()
                if (amount != EXACT_NAME) found += "${file.name}: approve amount is '$amount', expected '$EXACT_NAME'"
            }
        }
        return found
    }

    private companion object {
        const val EXACT_NAME = "needed"
        val MAX_VALUE = Regex("[fF]{64}")
        val APPROVE_CALL = Regex("""Erc20Abi\.approve(?:LoanBook)?\(([^;\n]*)\)""")
    }
}
