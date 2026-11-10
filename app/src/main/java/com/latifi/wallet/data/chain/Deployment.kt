package com.latifi.wallet.data.chain

/**
 * Where the app talks on chain and the limits of the contract. These are constants in code and
 * never come from an API response, so a tampered response cannot redirect a signed request.
 * `DeploymentTest` compares them with a copy of deployments/amoy.json of latifi-contracts.
 */
object Deployment {
    const val CHAIN_ID: Long = 80002

    /** CAIP-2 chain id used by the wallet session request. */
    const val CAIP_CHAIN_ID: String = "eip155:80002"

    const val LOANBOOK_ADDRESS: String = "0xccb8ad15a4954138c987a77924ceab468a843266"
    const val STABLECOIN_ADDRESS: String = "0x66757f1cc66b278bfc0eefad7e3cac1d70fd4f8b"

    const val EXPLORER_BASE: String = "https://amoy.polygonscan.com"

    const val MAX_RATE_BPS: Int = 5000
    const val MAX_TERM_DAYS: Int = 365
    const val MAX_INSTALLMENTS: Int = 6

    /** `mint` accepts at most 1,000 tokens per call. */
    const val MINT_PER_CALL_BASE_UNITS: Long = 1_000_000_000L

    /** `mint` accepts at most 10,000 tokens per address in total. */
    const val MINT_PER_WALLET_BASE_UNITS: Long = 10_000_000_000L
}
