# Apollo Android Regression Test Suite

This file is designed for an AI agent running regressions on an Android emulator using mobile automation tools (e.g., mobile-mcp). Each test case includes precise steps and pass criteria.

## Agent Setup

- **App package:** `io.muun.apollo.debug` (adjust flavor: `.local`, `.regtest`, `.dogfood` as needed)
- **Main activity:** `io.muun.apollo.presentation.ui.launch.LaunchActivity`
- **Before each test:** Take a screenshot to confirm current screen state
- **After each test:** Mark as PASS or FAIL with a brief note on what was observed
- **Between tests:** The app state carries over unless stated otherwise

### ADB helpers
```bash
# Launch app
adb shell am start -n io.muun.apollo.debug/io.muun.apollo.presentation.ui.launch.LaunchActivity

# Force stop (use only when a test requires fresh state)
adb shell am force-stop io.muun.apollo.debug

# Clear app data (use only when test requires clean install)
adb shell pm clear io.muun.apollo.debug
```

---

## Prerequisites

Some tests require:
- **Two simultaneous wallets** (Muun-to-Muun tests): Run two emulators or use a second physical device with the same app flavor and environment.
- **External LN wallet**: A lightning wallet (e.g., Phoenix, Breez) connected to the same regtest/dogfood environment.
- **Google Drive access**: A Google account configured on the emulator.
- **Email access**: Ability to check inbox for the test account's email.
- **Funds in wallet**: Some tests assume the wallet already has a balance. Run receive tests before spend tests.

---

## Test Cases

---

### TC-01: Create Wallet

**Platform:** Apollo (Android)
**Requires:** Clean install or logged-out state

**Steps:**
1. Launch the app.
2. On the welcome screen, tap **"Create a new wallet"**.
3. Wait for the PIN setup screen to appear.
4. Enter a 4-digit PIN (e.g., `1234`).
5. Re-enter the same PIN to confirm.
6. Wait for the home screen to load.

**Pass criteria:** Home screen is displayed showing a welcoming message (e.g., "Welcome to Muun") and a zero balance.

---

### TC-02: Set Up Email and Password

**Platform:** Apollo (Android)
**Requires:** Logged-in wallet (TC-01 completed)

**Steps:**
1. From the home screen, tap the **shield/security icon** (bottom navigation or top area).
2. Locate the **"Email and password"** step card.
3. Tap the card.
4. Enter a valid email address for the test account.
5. Check the inbox and enter the verification code received.
6. Enter a strong password.
7. Confirm the password.
8. Tap **"Set up"** or **"Continue"**.

**Pass criteria:** The email step card on the security screen turns green/complete.

---

### TC-03: Skip Email Step

**Platform:** Apollo (Android)
**Requires:** Logged-in wallet where email has not been set up

**Steps:**
1. From the home screen, tap the **shield/security icon**.
2. Locate the **"Email and password"** step card.
3. Tap on it.
4. Tap **"Skip"** or **"Not now"** (look for a skip/dismiss option).
5. Confirm the skip if a confirmation dialog appears.

**Pass criteria:** The email step card on the security screen is marked as skipped (distinct visual state, not green).

---

### TC-04: Set Up a Recovery Code (RC)

**Platform:** Apollo (Android)
**Requires:** Logged-in wallet

**Steps:**
1. From the home screen, tap the **shield/security icon**.
2. Locate the **"Recovery code"** step card.
3. Tap the card.
4. Follow the setup flow: read the instructions and tap **"Continue"**.
5. Note down the displayed 8-word recovery code (write it down for use in sign-in tests).
6. Confirm you have saved the code (tap checkboxes or confirmation button).
7. Enter the code on the verification screen to prove you saved it.

**Pass criteria:** The recovery code step card on the security screen turns green/complete.

---

### TC-05: Change User Password

**Platform:** Apollo (Android)
**Requires:** Email and password set up (TC-02 completed)

**Steps:**
1. From the home screen, tap the **hamburger menu** or **settings gear** icon.
2. Navigate to **"Security"** > **"Change password"** (or similar option).
3. Enter the current password.
4. Enter a new password.
5. Confirm the new password.
6. Tap **"Change"** or **"Save"**.
7. Log out of the app (Settings > **"Log out"**).
8. On the sign-in screen, tap **"Sign in"**, enter the email and the **new** password.
9. Enter the PIN when prompted.

**Pass criteria:** After logging in with the new password, the app advances to the PIN screen successfully and then to the home screen.

---

### TC-06: Sign In Using Email + Password

**Platform:** Apollo (Android)
**Requires:** Account with email and password set up; currently logged out

**Steps:**
1. If logged in: go to Settings > **"Log out"**, confirm log out.
2. On the welcome screen, tap **"Log in"** or **"Sign in"**.
3. Choose **"Use email"** or enter email directly.
4. Enter the test account email.
5. Enter the password.
6. Enter the PIN when prompted.

**Pass criteria:** App advances to the PIN screen, then to the home screen.

---

### TC-07: Sign In Using Email + RC

**Platform:** Apollo (Android)
**Requires:** Account with email and RC set up; currently logged out

**Steps:**
1. If logged in: go to Settings > **"Log out"**, confirm log out.
2. On the welcome screen, tap **"Log in"** or **"Sign in"**.
3. Choose the email sign-in option.
4. Enter the test account email.
5. When prompted for authentication, look for **"Use recovery code"** option.
6. Enter the recovery code (8 words separated by spaces or dashes).
7. Enter the PIN when prompted.

**Pass criteria:** App advances to the PIN screen successfully.

---

### TC-08: Sign In Using RC

**Platform:** Apollo (Android)
**Requires:** Account with RC set up; currently logged out

**Steps:**
1. If logged in: go to Settings > **"Log out"**, confirm log out.
2. On the welcome screen, tap **"Log in"** or **"Sign in"**.
3. Look for **"Use recovery code"** option (may be below email field).
4. Enter the full recovery code.
5. Enter the PIN when prompted.

**Pass criteria:** App advances to the PIN screen and then to home screen after the loading completes.

---

### TC-09: Log Out, Sign In Using RC + Email Confirmation

**Platform:** Apollo (Android)
**Requires:** Account with email set up; currently logged in

**Steps:**
1. Go to Settings > **"Log out"**, confirm log out.
2. On the welcome screen, tap **"Log in"** or **"Sign in"**.
3. Enter the test account email.
4. When prompted, select the **"Recovery code"** path (not password).
5. Enter the recovery code.
6. The app should send a confirmation email. Check the inbox.
7. Tap the confirmation link or enter the confirmation code in the app.
8. Enter the PIN when prompted.

**Pass criteria:** App advances to the PIN screen after email confirmation.

---

### TC-10: Sign In With RC Using an Old User

**Platform:** Apollo (Android)
**Requires:** Specific test account credentials (RC version 1)

**Credentials:**
- **Email:** Set via `TEST_OLD_USER_EMAIL` env var
- **RC:** Set via `TEST_OLD_USER_RC` env var
- **Note:** Password was reset via "Forgot password" flow

**Steps:**
1. If logged in: go to Settings > **"Log out"**, confirm log out.
2. On the welcome screen, tap **"Log in"** or **"Sign in"**.
3. Choose **"Use recovery code"**.
4. Enter the RC from `TEST_OLD_USER_RC` env var.
5. If prompted for email, enter the value from `TEST_OLD_USER_EMAIL` env var.
6. Complete any additional verification steps (email confirmation if required).
7. Enter the PIN when prompted.

**Pass criteria:** App advances to the PIN screen successfully.

---

### TC-11: Receive from Another Wallet to a Legacy Address

**Platform:** Apollo (Android)
**Requires:** External wallet with funds; logged in

**Steps:**
1. From the home screen, tap the **receive** button (down arrow icon).
2. On the receive screen, look for an address type selector.
3. Select **"Legacy"** address type (if selectable), or find the legacy address option.
4. Copy the displayed Bitcoin legacy address (starts with `1`).
5. In the external wallet, initiate a send to this address.
6. Return to the Apollo app and wait for the transaction to appear.

**Pass criteria:** A notification appears and/or a new operation is shown in the home screen with the received amount.

---

### TC-12: Receive from Another Wallet to a Segwit Address (Muun → Muun)

**Platform:** Apollo (Android)
**Requires:** Second Muun wallet instance; logged in on both

**Steps:**
1. On **Wallet A** (receiving): tap the **receive** button.
2. Select or confirm **segwit** address type (starts with `3` or `bc1`).
3. Set a fixed amount using the **"Set amount"** option (required for Bitcoin URI).
4. Copy the Bitcoin URI or address.
5. On **Wallet B** (sending): tap **"Send"**, paste the Bitcoin URI.
6. Confirm the amount is pre-filled.
7. Complete the send on Wallet B.
8. On **Wallet A**: watch the home screen for the incoming transaction.

**Pass criteria:** Balance on Wallet A is updated to match the sent amount.

---

### TC-13: Receive from a LN Wallet on an Invoice With Amount (Other Wallet → Muun)

**Platform:** Apollo (Android)
**Requires:** External lightning wallet; logged in
**Note:** Log out invalidates invoices — do not log out between generating invoice and paying it.

**Steps:**
1. On Apollo, tap the **receive** button.
2. Select **"Lightning"** or let the app generate a lightning invoice.
3. Set a specific amount (e.g., 10,000 sats).
4. Copy the lightning invoice (BOLT11 string starting with `lnbc...`).
5. On the external LN wallet, paste the invoice and send.
6. Return to Apollo and wait for the balance to update.

**Pass criteria:** Balance is updated to match the sent amount.

---

### TC-14: Receive from Another Muun Wallet on an Invoice With Amount (Muun → Muun)

**Platform:** Apollo (Android)
**Requires:** Two Muun wallet instances on same environment
**Note:** Log out invalidates invoices — do not log out during this test.

**Steps:**
1. On **Wallet A** (receiving): tap **receive**, select **Lightning**, set an amount (e.g., 5,000 sats), copy the invoice.
2. On **Wallet B** (sending): tap **send**, paste the lightning invoice, confirm amount, complete payment.
3. On **Wallet A**: wait for balance to update.

**Pass criteria:** Balance on Wallet A is updated to match the invoiced amount.

---

### TC-15: Send Money from a Lightning Wallet to an Amountless Invoice (Other Wallet → Muun)

**Platform:** Apollo (Android)
**Requires:** External lightning wallet; logged in
**Note:** Use amount > 10,000 sats (e.g., 10,001 sats). Do not log out.

**Steps:**
1. On Apollo, tap the **receive** button.
2. Select **"Lightning"**.
3. Generate an invoice **without** a fixed amount (leave amount blank).
4. Copy the invoice.
5. On the external LN wallet, paste the invoice, enter amount (e.g., 10,001 sats), and send.
6. Return to Apollo and wait for the balance update.

**Pass criteria:** Balance is updated to reflect the amount sent by the external wallet.

---

### TC-16: Send Money from Another Muun Wallet, Amountless Invoice (Muun → Muun)

**Platform:** Apollo (Android)
**Requires:** Two Muun wallet instances on same environment
**Note:** Do not log out during this test.

**Steps:**
1. On **Wallet A** (receiving): tap **receive**, select **Lightning**, generate an invoice with **no amount**, copy it.
2. On **Wallet B** (sending): tap **send**, paste the invoice, enter an amount, complete payment.
3. On **Wallet A**: wait for balance to update.

**Pass criteria:** Balance on Wallet A is updated to match the amount sent.

---

### TC-17: Monitor Confirmations

**Platform:** Apollo (Android)
**Requires:** A recent on-chain transaction (completed in a prior test)

**Steps:**
1. From the home screen, tap on a recent on-chain operation.
2. Note the current confirmation count (likely 0 or low).
3. Wait for the network to mine new blocks (in regtest: manually mine; in dogfood: wait).
4. Periodically pull-to-refresh or check the operation detail screen.

**Pass criteria:** Confirmation count on the operation increases over time (reaches at least 1 confirmation).

---

### TC-18: Try Spending All Funds Received

**Platform:** Apollo (Android)
**Requires:** Wallet with a positive balance

**Steps:**
1. From the home screen, tap **"Send"** or the send button.
2. Enter a destination address or lightning invoice.
3. On the amount screen, tap **"Use all funds"** (or a similar "max" button).
4. Confirm that the amount field is filled with the total available balance.
5. Confirm the fee estimate is shown.
6. Tap **"Continue"** and confirm the payment.

**Pass criteria:** Payment is sent successfully; balance approaches zero (accounting for fees).

---

### TC-19: Try Spending Every UTXO Received

**Platform:** Apollo (Android)
**Requires:** Wallet with multiple UTXOs (received multiple separate transactions)

**Steps:**
1. For each individual UTXO/operation received:
   a. Tap **"Send"**.
   b. Enter a destination address.
   c. Enter the amount of that specific UTXO (or use "Use all funds" for the last one).
   d. Confirm the send.
2. After each send, verify the operation appears in history.
3. Repeat until all UTXOs are spent.

**Pass criteria:** All individual UTXOs are successfully sent; balance reaches zero.

---

### TC-20: Try to Delete Wallet With Money

**Platform:** Apollo (Android)
**Requires:** Wallet with a positive balance

**Steps:**
1. From the home screen, tap the **settings** icon.
2. Scroll down to find **"Delete wallet"** or **"Logout and delete wallet"** option.
3. Tap the delete option.
4. Observe the dialog or alert that appears.

**Pass criteria:** An alert is displayed stating the wallet cannot be deleted because it has funds (e.g., "Empty your wallet first"). The wallet does **not** log out or delete.

---

### TC-21: Export a Kit Manually

**Platform:** Apollo (Android)
**Requires:** Logged-in wallet with email and RC set up

**Steps:**
1. From the home screen, tap the **shield/security icon**.
2. Tap **"Emergency kit"** or **"Export kit"** option.
3. Choose **"Export manually"** (not email or cloud storage).
4. Read and continue through any informational screens.
5. On the verification code screen, enter the displayed code to confirm you can access it.
6. The app generates a PDF or file of the kit.

**Pass criteria:** The verification code is accepted. The receiving app (file manager, share sheet) is able to handle the kit file.

---

### TC-22: (Apollo) Export a Kit With Self-Email

**Platform:** Apollo (Android) only

**Steps:**
1. From the home screen, tap the **shield/security icon**.
2. Tap **"Emergency kit"** or **"Export kit"** option.
3. Choose **"Send to my email"** option.
4. Confirm the email address shown is the account email.
5. Tap **"Send"**.
6. Check the email inbox for the kit email.
7. Verify the email has the correct subject line, body text, and a valid attachment.

**Pass criteria:** Email is received. The attachment is a valid emergency kit file. Subject and body match the expected format.

---

### TC-23: Export a Kit With Google Drive

**Platform:** Apollo (Android)
**Requires:** Google account configured on the emulator

**Steps:**
1. Go to Google Drive on the emulator and delete any existing Muun kits in the Muun folder.
2. In the emulator's account settings, remove Muun's Google Drive permission (optional for a clean test).
3. Return to Apollo. From the home screen, tap the **shield/security icon**.
4. Tap **"Emergency kit"** or **"Export kit"** option.
5. Choose **"Upload to Google Drive"**.
6. A Google account picker appears — select the account and accept permissions.
7. Wait for the automatic upload to complete.
8. Tap **"Open in Drive"** (if shown) or open Google Drive manually.
9. Navigate to the Muun folder.

**Pass criteria:** The kit is correctly uploaded to the Muun folder in Google Drive.

---

### TC-24: Unified QR — Setup

**Platform:** Apollo (Android)

**Steps:**
1. From the home screen, tap **Settings** (gear icon).
2. Navigate to **"Receive preferences"** or **"Bitcoin address type"** settings.
3. Test switching between **Lightning**, **On-chain**, and **Unified QR** options.
4. For each option, go back to the receive screen and generate a QR code.
5. Verify that:
   - Lightning → QR shows a Lightning invoice.
   - On-chain → QR shows a Bitcoin address.
   - Unified QR → QR shows a BIP-21 URI with both address and Lightning invoice.

**Pass criteria:** The QR changes correctly based on the setting. With Unified QR, the remaining capacity/amount info is visible.

---

### TC-25: Unified QR — On-Chain (Send From External Wallet)

**Platform:** Apollo (Android)
**Requires:** External wallet that supports BIP-21 unified QRs

**Steps:**
1. In Apollo settings, enable **Unified QR** mode.
2. Go to the receive screen and set a fixed amount.
3. Display the QR code.
4. On the external wallet, scan or paste the QR/URI.
5. Verify that the external wallet correctly parses:
   - The Bitcoin address.
   - The pre-filled amount.
6. The external wallet sends on-chain.
7. Wait for Apollo to receive the transaction.

**Pass criteria:** External wallet correctly displays the address and auto-fills the amount.

---

### TC-26: Unified QR — Off-Chain, Muun to External (M2E)

**Platform:** Apollo (Android)
**Requires:** External lightning wallet (e.g., Phoenix)

**Steps:**
1. In Apollo settings, enable **Unified QR** mode.
2. Go to the receive screen and set an amount.
3. Display the unified QR code.
4. On the external lightning wallet (e.g., Phoenix), scan the QR.
5. The external wallet should offer a choice of send type (Lightning or on-chain).
6. Choose Lightning.
7. Verify the amount is pre-filled.
8. Complete the send from the external wallet.
9. Check Apollo for the received funds.

**Pass criteria:** External wallet can see both address and amount. Payment completes successfully.

---

### TC-27: Unified QR — Off-Chain, Muun to Muun (M2M)

**Platform:** Apollo (Android)
**Requires:** Two Muun wallet instances

**Steps:**
1. On **Wallet A** (receiving): enable Unified QR in settings. Set an amount. Go to receive screen.
2. On **Wallet B** (sending): tap **send**, scan or paste the QR from Wallet A.
3. Wallet B should default to sending via Lightning.
4. Confirm amount and complete the payment.
5. On Wallet A: wait for balance to update.

**Pass criteria:** Wallet B defaults to Lightning payment. Balance on Wallet A is updated.

---

### TC-28: Verify Balance Is Updated Correctly

**Platform:** Apollo (Android)
**Requires:** Run this after completing send/receive tests

**Steps:**
1. On the home screen, note the displayed balance.
2. Open the operation history (tap "Activity" or similar).
3. Sum up all received amounts minus all sent amounts and fees.
4. Compare the calculated balance with the displayed balance.
5. Also verify the balance displays correctly in both BTC and local currency.

**Pass criteria:** Displayed balance matches the sum of all operations. Currency conversion is shown correctly.

---

### TC-29: Update App

**Platform:** Apollo (Android)
**Requires:** Previous app version installed with a logged-in user

**Steps:**
1. Ensure the app has a previous version installed with a logged-in user and some operations in history.
2. Note the current balance and last operation.
3. Install the new version of the APK:
   ```bash
   adb install -r path/to/new-version.apk
   ```
4. Launch the app.
5. Navigate to the home screen.

**Pass criteria:** The app launches successfully without crashes. Balance and operation history are intact. No data migration errors are shown.

---

### TC-30: Use the Recovery Tool to Recover Funds

**Platform:** Apollo (Android)
**Requires:** RC and Emergency Kit (both keys from the EK); access to Muun Recovery Tool

**Steps:**
1. Obtain the user's Recovery Code (from TC-04 setup).
2. Obtain the Emergency Kit file (exported in TC-21 or TC-22).
3. Open the Muun Recovery Tool (standalone app or web tool at the Muun recovery URL).
4. Follow the tool's instructions:
   a. Input the Recovery Code.
   b. Upload or provide both keys from the Emergency Kit.
5. The tool should display the user's funds and allow recovery.
6. Initiate the recovery transaction to a destination address.
7. Compare the amount shown in the recovery tool against the Apollo app balance.

**Pass criteria:** A recovery transaction is made successfully. Funds shown in the recovery tool are greater than or equal to the funds shown in the Apollo app.

---

## Test Execution Order (Recommended)

Run tests in this order to build on prerequisite state:

1. TC-01 Create Wallet
2. TC-02 Set Up Email and Password
3. TC-04 Set Up RC
4. TC-03 Skip Email Step *(requires fresh wallet — run independently)*
5. TC-05 Change User Password
6. TC-11 Receive to Legacy Address
7. TC-12 Receive Segwit (Muun → Muun)
8. TC-13 Receive LN Invoice With Amount (External → Muun)
9. TC-14 Receive LN Invoice With Amount (Muun → Muun)
10. TC-15 Receive Amountless LN Invoice (External → Muun)
11. TC-16 Receive Amountless LN Invoice (Muun → Muun)
12. TC-28 Verify Balance
13. TC-17 Monitor Confirmations
14. TC-18 Try Spending All Funds
15. TC-19 Try Spending Every UTXO
16. TC-20 Try to Delete Wallet With Money *(run before spending all funds)*
17. TC-21 Export Kit Manually
18. TC-22 Export Kit via Self-Email
19. TC-23 Export Kit via Google Drive
20. TC-24 Unified QR Setup
21. TC-25 Unified QR On-Chain
22. TC-26 Unified QR M2E
23. TC-27 Unified QR M2M
24. TC-06 Sign In Email + Password
25. TC-07 Sign In Email + RC
26. TC-08 Sign In RC
27. TC-09 Log Out + Sign In RC + Email Confirmation
28. TC-10 Sign In Old User with RC
29. TC-29 Update App *(requires two APK versions)*
30. TC-30 Recovery Tool
