package com.example.android.data.tips

import java.util.Calendar

data class SecurityTip(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val details: String,
    val warningSigns: List<String>,
    val recommendedAction: String
)

object SecurityTipsProvider {

    val tips = listOf(
        SecurityTip(
            id = "TIP-01",
            title = "Beware of Urgent Package Delivery SMS",
            category = "Smishing",
            summary = "Scammers send fake failed delivery or pending customs alerts urging you to click a link.",
            details = "Fraudsters impersonate courier companies (like FedEx, DHL, India Post, or USPS) stating your parcel cannot be delivered until you update your address or pay a small re-delivery fee. The link leads to a credential or credit card harvesting page.",
            warningSigns = listOf(
                "Shortened or unusual link domains (e.g., bit.ly, tinyurl, or random alphanumeric domains).",
                "Artificial urgency ('Delivery cancelled in 12 hours').",
                "Requests for immediate payment or debit card details."
            ),
            recommendedAction = "Never click links in SMS. Always track packages directly on the carrier's official mobile app or official website."
        ),
        SecurityTip(
            id = "TIP-02",
            title = "Never Scan Untrusted QR Codes",
            category = "Quishing",
            summary = "Malicious QR codes can secretly redirect your browser to credential-stealing phishing sites.",
            details = "Attackers paste fake QR code stickers over legitimate parking meters, restaurant menus, or send them via email masquerading as 2-Factor Authentication reset prompts or payment confirmations.",
            warningSigns = listOf(
                "Physical stickers placed on top of official signs.",
                "QR codes received via unsolicited emails asking you to 'Verify Identity'.",
                "Unusual or long redirect URLs displayed after scanning."
            ),
            recommendedAction = "Always preview the destination URL inside Squish Scanner before opening it. Never input banking passwords after scanning a QR code."
        ),
        SecurityTip(
            id = "TIP-03",
            title = "Banks Never Ask for Your OTP or PIN",
            category = "Impersonation",
            summary = "No legitimate bank or payment app representative will ever call or text requesting an OTP.",
            details = "In modern OTP theft schemes, attackers trigger an unauthorized password reset or fund transfer, then contact you claiming to be 'Fraud Department Prevention' asking for the code sent to your phone to 'cancel' the transaction.",
            warningSigns = listOf(
                "Callers claiming an unauthorized charge occurred and asking for SMS verification codes.",
                "Callers creating extreme panic or rushing your responses.",
                "Messages reading 'Do NOT share this code with anyone'."
            ),
            recommendedAction = "Disconnect the call immediately. Call your bank using the official phone number printed on the back of your debit/credit card."
        ),
        SecurityTip(
            id = "TIP-04",
            title = "Verify Lookalike and Homograph Domains",
            category = "Phishing URLs",
            summary = "Attackers register domain names that look nearly identical to popular brands.",
            details = "Lookalike domains swap subtle characters, such as replacing 'l' (lowercase L) with '1' (one), or using Cyrillic characters (e.g., 'а' instead of Latin 'a') or adding extra words like 'secure-login-bank.com'.",
            warningSigns = listOf(
                "Spelling errors in company names within the address bar.",
                "Hyphen-separated brand names (e.g., paypal-verification-center.com).",
                "IP-address-based URLs (e.g., http://192.168.1.1/login)."
            ),
            recommendedAction = "Check the domain extension carefully. Bookmark official banking and service portals rather than following links in messages."
        ),
        SecurityTip(
            id = "TIP-05",
            title = "Beware of UPI 'Collect' Money Requests",
            category = "Payment Scams",
            summary = "Entering your UPI PIN always sends money — it is never required to receive funds.",
            details = "Scammers pretending to buy items from online marketplaces (OLX, Facebook Marketplace) send a UPI 'Collect Request' claiming they are transferring an advance deposit to you.",
            warningSigns = listOf(
                "Prompts requesting you to enter your UPI PIN to 'receive' or 'accept' a refund.",
                "QR codes sent via WhatsApp with claims like 'Scan to get payment'.",
                "Over-eager buyers offering immediate full payment without inspecting goods."
            ),
            recommendedAction = "Remember: Entering your UPI PIN deducts money from your bank account. Receiving money never requires a PIN."
        ),
        SecurityTip(
            id = "TIP-06",
            title = "Spotting Fake Electricity & Utility Disconnection SMS",
            category = "Smishing",
            summary = "Threat actors send panic-inducing notices claiming your power will be cut tonight.",
            details = "These messages provide an unofficial mobile number to call to 'update your bill' and install a remote desktop support app (AnyDesk, TeamViewer) to drain your bank accounts.",
            warningSigns = listOf(
                "SMS sent from random 10-digit mobile numbers rather than an official utility sender ID.",
                "Immediate threat of disconnection within a few hours.",
                "Instruction to contact an individual personal phone number."
            ),
            recommendedAction = "Check your current electricity bill status directly through your state utility provider's verified portal or electricity board app."
        ),
        SecurityTip(
            id = "TIP-07",
            title = "Remote Support App Scam Red Flags",
            category = "Social Engineering",
            summary = "Never install AnyDesk, TeamViewer, or QuickSupport on instruction from an unknown caller.",
            details = "Fraudsters impersonating customer care or technical support will instruct you to install remote management software, giving them complete remote control over your screen and OTPs.",
            warningSigns = listOf(
                "Customer service contacts claiming they need screen access to fix an issue.",
                "Callers asking for a 9-digit remote access code.",
                "Instructions to open your banking app while someone is remotely connected."
            ),
            recommendedAction = "Never grant remote screen-sharing permissions to third parties. Legitimate support staff will never request remote access to your personal phone."
        ),
        SecurityTip(
            id = "TIP-08",
            title = "Unsolicited Job & Work-From-Home Offers",
            category = "Scams",
            summary = "Offers promising high daily income for liking YouTube videos or rating hotels are scams.",
            details = "These 'Task Scams' lure victims with small initial payouts, then demand cryptocurrency or wire transfers into 'VIP deposit accounts' before freezing your money permanently.",
            warningSigns = listOf(
                "Job offers arriving unexpectedly via WhatsApp or Telegram.",
                "Offers of $200–$500 daily for minimal effort.",
                "Demands to pay registration or security fees to unlock your salary."
            ),
            recommendedAction = "Block and report the sender immediately. Legitimate companies never conduct recruitment solely through unsolicited instant messaging."
        )
    )

    fun getTodayTip(): SecurityTip {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val index = (dayOfYear - 1) % tips.size
        return tips[index]
    }

    fun getAllTips(): List<SecurityTip> = tips
}
