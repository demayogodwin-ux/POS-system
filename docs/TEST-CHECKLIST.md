# My Store POS - Manual Test Checklist

Run before every release. Make sure .env points at the Supabase project and the PC is online.
Starter accounts: **admin / 1234** (Admin), **cashier / 0000** (Cashier).
Write the date, tester and result (Pass / Fail + note) next to each step.

Tester: ____________  Date: ____________  Version: 1.0.0

## 0. Automated tests

| # | Step | Expected | Result |
|---|------|----------|--------|
| 0.1 | `mvn clean package` | `Tests run: 37, Failures: 0` and BUILD SUCCESS | |

## 1. Wrong PIN lockout

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1.1 | Type letters or a 7th digit in the PIN field | Nothing is typed | |
| 1.2 | Sign in as `admin` with PIN `9999` | Red "Wrong username or PIN" under the PIN; PIN cleared and focused | |
| 1.3 | Fail 4 more times (5 in total) | Sign in disabled; "Too many attempts. Try again in 30s" counts down | |
| 1.4 | Press Enter during the countdown | Nothing happens | |
| 1.5 | Wait for the countdown to end, sign in with `1234` | Shell opens on the Sale screen | |
| 1.6 | Deactivate `cashier` in Settings, try to sign in as cashier | "Wrong username or PIN" | |

## 2. Sale by barcode scanner

| # | Step | Expected | Result |
|---|------|----------|--------|
| 2.1 | Scan a product barcode (or type `4800016644290` and press Enter) | 1 added to the cart, field cleared, beep if enabled | |
| 2.2 | Scan the same barcode again | Quantity becomes 2x; line total doubles | |
| 2.3 | Scan an unknown code | Red "No product with code ..." for a few seconds; field cleared | |
| 2.4 | Press F2 from anywhere on the screen | Cursor goes to the scan field | |
| 2.5 | Type part of a name, then Esc | Table filters; Esc clears the field | |

## 3. Overselling blocked

| # | Step | Expected | Result |
|---|------|----------|--------|
| 3.1 | Look at an out-of-stock product (Safeguard Soap) | Row dimmed; "+" disabled; double-click does nothing | |
| 3.2 | Add a product with stock 3 (Zonrox) and press + on its cart row 3 times | Stops at 3x; "Only 3 of ... in stock" | |
| 3.3 | Right-click > Change quantity... > 10 | Refused with the stock message | |
| 3.4 | Put 3 in the cart, set its stock to 1 in phpMyAdmin, Checkout > Complete | "Not enough stock for ..."; cart kept; table shows 1; no new row in `sales` | |

## 4. Short cash

| # | Step | Expected | Result |
|---|------|----------|--------|
| 4.1 | Checkout (F12), type less than the amount due | Red "Short by ..." box; Complete Sale disabled; Enter does nothing | |
| 4.2 | Type the exact amount | Change 0.00; Complete enabled | |
| 4.3 | Click the next 100 / 500 / 1,000 buttons | Field filled; change updates | |
| 4.4 | Switch to Card | Cash and change boxes hidden; Complete enabled | |
| 4.5 | Esc | Dialog closes; cart unchanged | |

## 5. Receipt print

| # | Step | Expected | Result |
|---|------|----------|--------|
| 5.1 | Complete a cash sale | "Payment complete", "Give the customer ... change", receipt preview with store name, receipt no, items, totals, cash, change, footer | |
| 5.2 | Print Receipt > Microsoft Print to PDF | PDF looks like the preview and fits the page | |
| 5.3 | New Sale | Cart empty; stock updated in the table; cursor in scan field | |
| 5.4 | Settings: turn on "Print receipt after each sale", make a sale | Prints to the default printer without a dialog | |
| 5.5 | History: select the sale > Reprint Receipt | Same receipt prints again | |

## 6. Void returns stock (Admin)

| # | Step | Expected | Result |
|---|------|----------|--------|
| 6.1 | Note a product's stock, sell 2 of it | Stock down by 2 | |
| 6.2 | History > select that sale > Void sale > OK | Chip "Void"; cards drop by that sale; Void button disappears | |
| 6.3 | Products | Stock back to the noted value | |
| 6.4 | Sign in as cashier, open History | No "Void sale" button | |

## 7. Product add / edit / delete (Admin)

| # | Step | Expected | Result |
|---|------|----------|--------|
| 7.1 | Add Product with empty code, empty name, price `1.234`, stock `2.5` | Each refused with a red field and message | |
| 7.2 | Add Product with an existing barcode | "This code is already used" | |
| 7.3 | Add a valid product | Appears selected; cards and "N items - M low stock" update | |
| 7.4 | Edit its stock to at or below the low-stock level | Amber reorder note; chip "Low stock" after save | |
| 7.5 | Delete it (confirm) | Gone from Products and Sale; `active = 0` in phpMyAdmin; old sales still show it | |

## 8. Tax change in Settings (Admin)

| # | Step | Expected | Result |
|---|------|----------|--------|
| 8.1 | Settings: Save is disabled until something changes; Discard restores | As described | |
| 8.2 | Tax rate `101` or `abc` | Refused with a red field | |
| 8.3 | Put items in the cart, set tax to `10`, Save, go to Sale | "Tax 10%" and totals already recalculated | |
| 8.4 | phpMyAdmin `settings.tax_rate` | `0.1` | |
| 8.5 | Turn on "Prices include tax", Save, go to Sale | Total equals the shelf prices; tax shown as "(incl.)" | |
| 8.6 | Set tax back to `12`, include tax off, Save | Back to normal | |

## 9. Installed app

| # | Step | Expected | Result |
|---|------|----------|--------|
| 9.1 | Run `build-installer.bat`, install `MyStorePOS-1.0.0.exe` | Start menu + desktop shortcut | |
| 9.2 | Edit `%LOCALAPPDATA%\MyStorePOS\app\.env` (DB_URL) to a wrong port, start the app | Sign in shows the "Could not sign in" alert | |
| 9.3 | Put the right port back, restart | Sign in works | |
