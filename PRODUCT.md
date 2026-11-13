# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Kotlin, Jetpack Compose, Material 3 (BOM 2026.09.00, material3 1.4), Hilt, Navigation Compose. Single module `app`, min SDK 26, target SDK 37.

## Users
Two roles in one app. Borrowers in Latin America who need a small loan and want to build a portable reputation. Lenders who fund those requests and follow repayments. Both use a phone, often on mobile data, with a crypto wallet that signs each action. Spanish (es-419) and English are first class.

## Product Purpose
LatiFi Wallet is the mobile client of the LatiFi peer to peer lending platform on Polygon Amoy. A person requests a loan, funds one, pays installments and watches a reputation score grow. Success is a person completing a loan cycle and understanding what happened at every step, with no jargon.

## Positioning
Lending where reputation travels with the person and every money step is explained in plain words, with the chain as plumbing and never as vocabulary.

## Operating Context
The app starts in Demo mode with a fake wallet and invented data, so it can be shown without a real wallet. Live mode talks to the LatiFi API and a Reown wallet session. Every transaction passes through one status sheet (pending, confirming, confirmed, failed). Amounts are test tokens (tUSD) with a local currency estimate.

## Capabilities and Constraints
- Screens: onboarding, connect wallet, profile form, Home, Loans, Reputation, Invest feed, Portfolio, Profile, request a loan, fund, pay, transaction status sheet.
- Business, API, wallet and chain code are out of scope for visual work.
- Strings live in `res/values` and `res/values-b+es+419` and stay in sync. Copy guards forbid generic button labels and chain jargon.
- Dynamic color is never used, brand colors are fixed.
- Release builds must run Live mode.

## Brand Commitments
Navy `#0B1F3A`, navy 700 `#14407A`, lime `#C6FF3D` and `#D8FF7A`, green `#0A7D5A`. Sora for headings, Inter for body. The mark is the isotype: a lime pulse line with two dots on a navy rounded square, shared with the web favicon. Tone: trustworthy and calm, with a bright lime pulse of energy.

## Evidence on Hand
No real user research. Accessibility rules and decisions are in `docs/A11Y-DECISIONS.md`, `docs/EXCEPTIONS.md` and `docs/DEVICE-CHECKLIST.md`. Assumption (inferred from the brief, not confirmed in an interview): the audience is first time digital lenders and borrowers.

## Product Principles
1. Money screens are calm and legible first, expressive second.
2. Every status shows an icon, a word and a color, never color alone.
3. Motion confirms an action or explains a change, and it disappears under reduced motion.
4. One primary action per screen.
5. Plain words: the chain is never named in the interface.

## Accessibility & Inclusion
A11Y.md (WCAG 2.2 AA) over a WCAG 2.1 AA floor. 48dp targets, font scale up to 200 percent, TalkBack names for every control, contrast verified in unit tests for light and dark.
