# Requirement traceability

This table maps the nine Phase 5 requirements to the screens that deliver them and to the test classes that prove them. Every class name below exists in `app/src/test/java` (JVM) or `app/src/androidTest/java` (emulator). A test marked "emulator" runs in the advisory `android-instrumented` workflows, not in the merge gate. To re-check the names after a rename, run this from the repository root. It prints every backticked class name of this file that is not a test file:

```
for n in $(grep -o '`[A-Za-z0-9]*Test`' docs/TRACEABILITY.md | tr -d '`' | sort -u); do
  find app/src/test app/src/androidTest -name "$n.kt" | grep -q . || echo "missing: $n"
done
```

## What this evidence is, and what it is not

Success criteria 1 and 2 of Phase 5 (a wallet connects through Reown and the session survives a process kill, and a borrower and a lender complete a loan cycle from the app) are verified here only against fakes and MockWebServer. The wallet gateway is tested through a fake SDK adapter, the API through MockWebServer and a copy of the OpenAPI contract, the chain through a fake JSON-RPC node, and the Keystore envelope on an emulator. Nothing was run against a real wallet, a deployed API or live Polygon Amoy. Those runs are the human checks in [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md) and stay pending until Juan creates a Reown project and plan 03-08 funds the demo wallets.

## The nine requirements

| ID | Requirement in short | Screens | Plans | Proving tests |
| -- | -------------------- | ------- | ----- | ------------- |
| AUTH-01 | Connect a wallet to authenticate | S02 Connect, S03 Sign-in sheet, S17 Profile | 05-05, 05-06, 05-13, 05-14 | `ConnectViewModelTest`, `SignInUseCaseTest`, `SignInViewModelTest`, `ReauthCoordinatorTest`, `ApiAuthRepositoryTest`, `ReauthAuthenticatorTest`, `AuthInterceptorTest`, `AppKitWalletGatewayTest`, `KeystoreSessionStoreTest`, `AppStartupTest`, `ConnectScreenTest` (emulator), `SignInSheetTest` (emulator), `SessionEnvelopeTest` (emulator), `AppLaunchSmokeTest` (emulator) |
| AUTH-02 | Sign from the app without LatiFi holding a private key (non-custodial) | S02, S03, S17, S20 Disconnect dialog | 05-01, 05-05, 05-06, 05-14 | `NoKeyMaterialTest`, `NoSecretsInLogsTest`, `AppKitWalletGatewayTest`, `WalletRequestParamsTest`, `SignInUseCaseTest`, `SignOutUseCaseTest`, `ProfileViewModelTest`, `DataModuleBindingsTest`, `KeystoreSessionStoreTest`, `SessionEnvelopeTest` (emulator), `ProfileScreenTest` (emulator) |
| AUTH-03 | Plain language onboarding before the first connection | S01 Onboarding | 05-02, 05-05 | `OnboardingViewModelTest`, `NoJargonTest`, `NoGenericLabelsTest`, `NoHardcodedUiTextTest`, `StringsParityTest`, `StartDestinationResolverTest`, `OnboardingFlowTest` (emulator), `AppLaunchSmokeTest` (emulator) |
| AUTH-04 | Clear transaction states: pending, confirming, confirmed, failed | S19 Transaction Status sheet, S10, S14, S18 | 05-11, 05-12, 05-14 | `TransactionCoordinatorTest`, `TransactionTimeoutTest`, `TransactionFailureTest`, `TransactionResumerTest`, `WalletWaitTimerTest`, `ReceiptPollerTest`, `IndexerLagWaiterTest`, `FailureActionsTest`, `DataStoreRecoveryStoreTest`, `TxStatusViewModelTest`, `TxPlansTest`, `AppKitWalletGatewayTest`, `AppStartupTest`, `TxStatusSheetTest` (emulator) |
| IDEN-01 | Light profile (name, contact) before publishing or funding | S05 Profile form, S17 Profile | 05-06, 05-13 | `ProfileFormViewModelTest`, `ProfileValidatorTest`, `ProfileGateTest`, `PostSignInUseCaseTest`, `ProfileViewModelTest`, `ApiProfileRepositoryTest`, `ProfileFormScreenTest` (emulator), `ProfileScreenTest` (emulator) |
| LEND-02 | Lender sees a feed of open requests with the borrower reputation | S12 Invest feed, S13 Request details | 05-09, 05-10, 05-13 | `FeedViewModelTest`, `FeedQueryMapperTest`, `FeedOrderingTest`, `BorrowerNameResolverTest`, `RequestDetailViewModelTest`, `ApiLoanRepositoryTest`, `ApiReputationRepositoryTest`, `FeedScreenTest` (emulator), `RequestDetailScreenTest` (emulator) |
| LEND-05 | Both roles see the loan status at any time (active, paid, overdue) | S06 Home, S07 Loans, S08 Loan details, S15 Portfolio, S16 Investment details | 05-03, 05-04, 05-07, 05-08, 05-10, 05-13 | `LoanStatusMappingTest`, `InstallmentScheduleTest`, `LoansViewModelTest`, `LoanDetailViewModelTest`, `PortfolioViewModelTest`, `InvestmentDetailViewModelTest`, `HomeViewModelTest`, `ApiLoanRepositoryTest`, `NavigationModelTest`, `LoansScreenTest` (emulator), `LoanDetailScreenTest` (emulator), `PortfolioScreenTest` (emulator), `InvestmentDetailScreenTest` (emulator), `HomeScreenTest` (emulator) |
| LEND-06 | Amounts and installments in local currency next to the stablecoin | S06, S07, S08, S10, S12, S13, S15 | 05-03, 05-07, 05-08, 05-09, 05-13 | `LocalCurrencyTest`, `LocalValueProviderTest`, `TokenAmountTest`, `LoanMathTest`, `ApiExchangeRateRepositoryTest`, `HomeViewModelTest`, `LoansViewModelTest`, `FeedViewModelTest`, `PortfolioViewModelTest` |
| REP-03 | The feed orders or highlights requests by the borrower reputation level | S12, S13, S11 Reputation | 05-08, 05-09, 05-10 | `FeedOrderingTest`, `FeedViewModelTest`, `FeedQueryMapperTest`, `ReputationLevelTest`, `ReputationFormatTest`, `ReputationViewModelTest`, `ApiReputationRepositoryTest`, `FeedScreenTest` (emulator), `ReputationScreenTest` (emulator) |

## Requirements integrated by the app

These requirements belong to earlier phases or to the API. The app calls them, and the tests below show that each call works against the contract or a fake.

| ID | Delivered by | Where the app calls it | Tests |
| -- | ------------ | ---------------------- | ----- |
| LEND-01 | Smart contract (Phase 3) | S09 Request a loan, `requestLoan` through the wallet | `RequestLoanViewModelTest`, `LoanRequestCalculatorTest`, `TxPlansTest`, `LoanBookAbiTest`, `FakeChainSimulatorTest`, `RequestLoanScreenTest` (emulator) |
| LEND-03 | Smart contract (Phase 3) | S14 Fund confirmation, `approve` then `fund` | `FundViewModelTest`, `TxPlansTest`, `NoUnlimitedApproveTest`, `Erc20AbiTest`, `PayFundScreenTest` (emulator) |
| LEND-04 | Smart contract (Phase 3) | S10 Pay installment, `approve` then `repay` | `PayViewModelTest`, `TxPlansTest`, `NoUnlimitedApproveTest`, `FakeChainSimulatorTest`, `PayFundScreenTest` (emulator) |
| IDEN-02 | LatiFi API (Phase 4) | `GET` and `PUT /profiles/{address}` | `ApiProfileRepositoryTest`, `ContractFixtureTest`, `OperationIdsTest` |
| API-01 | LatiFi API (Phase 4) | `GET /reputation/{address}` and its history | `ApiReputationRepositoryTest`, `ContractFixtureTest`, `OperationIdsTest` |
| API-02 | LatiFi API (Phase 4) | `GET /rates` and the conversion | `ApiExchangeRateRepositoryTest`, `LocalValueProviderTest`, `ContractFixtureTest` |
| REP-02 | LatiFi API (Phase 4) | S11 Reputation, history on S13 | `ReputationViewModelTest`, `ApiReputationRepositoryTest`, `ReputationScreenTest` (emulator) |

## Guards that protect the evidence

| Guard | What it stops |
| ----- | ------------- |
| `NoKeyMaterialTest` | Any import of a web3j signing class, or a name such as private key, mnemonic or seed phrase, in `src/main/java` |
| `NoSecretsInLogsTest` | A log call that names a token, a signature, the authorization header or the RPC URL |
| `RoutesRegistryTest` | A route or an overlay that is declared but not registered. Both pending sets are empty |
| `OperationIdsTest` and `ContractFixtureTest` | Drift between the Retrofit interface and the copy of the OpenAPI 0.2.0 contract |
| `StringsParityTest`, `NoHardcodedUiTextTest`, `NoJargonTest`, `NoGenericLabelsTest` | A string missing in one language, text in code, crypto jargon, or a generic button label |
| `ThemeContrastTest`, `TypographyTokensTest` | A color pair under its contrast ratio, or a type token that left the ratified scale |
