package com.example.android.presentation.education

data class SecurityLesson(
    val id: String,
    val title: String,
    val category: String,
    val iconName: String,
    val readTimeMinutes: Int,
    val explanation: String,
    val warningSigns: List<String>,
    val realisticScenario: String,
    val protectiveSteps: List<String>,
    val quizQuestion: String,
    val quizOptions: List<String>,
    val correctOptionIndex: Int,
    val quizExplanation: String
)

object EducationRepository {

    val lessons = listOf(
        SecurityLesson(
            id = "LES-01",
            title = "Smishing & Fake Delivery Alerts",
            category = "SMS Phishing",
            iconName = "LocalShipping",
            readTimeMinutes = 2,
            explanation = "Smishing is the use of deceptive SMS messages to trick victims into downloading malware, sharing credit card numbers, or handing over account credentials.",
            warningSigns = listOf(
                "Unusual sender numbers or unknown shortcodes.",
                "Claims of a package held at customs with a tiny $1–$2 redelivery fee.",
                "Obfuscated URLs using link-shortening services or fake tracking domains.",
                "Strict deadlines demanding compliance within hours."
            ),
            realisticScenario = "\"USPS Alert: Your package #US94029 cannot be delivered due to incomplete street address. Please update within 12h at: bit.ly/usps-track-910 or it will be returned to sender.\"",
            protectiveSteps = listOf(
                "Never tap links inside SMS notifications.",
                "Visit the delivery carrier's official website directly and enter your tracking code.",
                "Use Squish Message Scanner to inspect the message text.",
                "Report and block the sender."
            ),
            quizQuestion = "You receive an SMS saying your parcel has an address issue with a link. What should you do?",
            quizOptions = listOf(
                "Click the link immediately to prevent parcel return",
                "Reply to the SMS asking for confirmation",
                "Ignore the link and check the tracking number directly on the official postal website",
                "Forward the SMS to family members"
            ),
            correctOptionIndex = 2,
            quizExplanation = "Never click links in delivery SMS. Always check tracking IDs on verified official carrier apps or portals."
        ),
        SecurityLesson(
            id = "LES-02",
            title = "Quishing (QR Code Phishing)",
            category = "QR Security",
            iconName = "QrCodeScanner",
            readTimeMinutes = 3,
            explanation = "Quishing attacks disguise malicious phishing URLs inside standard QR codes. Because human eyes cannot read QR encodings, users cannot spot fake domains until after scanning.",
            warningSigns = listOf(
                "Physical stickers pasted over legitimate parking meters or storefront displays.",
                "QR codes received in unsolicited corporate or banking emails asking for 2FA verification.",
                "Decoded URLs with mismatched domains or unusual top-level domains."
            ),
            realisticScenario = "A flyer on a public electric car charger invites drivers to: \"Scan to pay charging fee quickly.\" The sticker was secretly placed by a criminal over the real charger QR code, redirecting payments to an offshore account.",
            protectiveSteps = listOf(
                "Check for physical tampering or peeling stickers on public QR codes.",
                "Use the Squish QR Scanner to preview the domain before your browser opens it.",
                "Never enter banking or login credentials after scanning a QR code.",
                "When possible, pay via verified official apps rather than arbitrary web links."
            ),
            quizQuestion = "Why are QR codes particularly risky for phishing attacks?",
            quizOptions = listOf(
                "QR codes contain viruses that execute immediately upon aiming the camera",
                "Human beings cannot read the encoded URL before scanning, hiding malicious domains",
                "QR codes can only be scanned once",
                "QR codes cannot encode web links"
            ),
            correctOptionIndex = 1,
            quizExplanation = "Because the encoded text is unreadable to the human eye, attackers hide lookalike or malicious domains behind standard QR images."
        ),
        SecurityLesson(
            id = "LES-03",
            title = "Fake Banking & UPI Payment Alerts",
            category = "Financial Safety",
            iconName = "AccountBalance",
            readTimeMinutes = 3,
            explanation = "Fraudsters impersonate financial institutions claiming your account is frozen, PAN/KYC is expired, or an urgent refund is pending to siphon funds.",
            warningSigns = listOf(
                "Threats that your account will be suspended today if KYC is not updated immediately.",
                "Links directing to unverified domains mimicking official net-banking portals.",
                "Demands to enter your UPI PIN to 'receive' or 'claim' funds."
            ),
            realisticScenario = "\"Dear Customer, your HDFC netbanking access will be blocked today due to pending PAN card verification. Update now at: hdfc-kyc-verify-portal.in to avoid disruption.\"",
            protectiveSteps = listOf(
                "Banks NEVER send SMS links for KYC or PAN updates.",
                "Log in strictly through your bank's verified mobile app or bookmarked netbanking website.",
                "Remember: You NEVER enter your PIN to receive money on UPI.",
                "Contact your branch manager directly if in doubt."
            ),
            quizQuestion = "A buyer online says they sent you a $50 deposit via UPI and asks you to enter your PIN to accept it. What is happening?",
            quizOptions = listOf(
                "Entering your PIN will credit $50 to your account",
                "This is a scam; entering your PIN authorizes money to leave your account",
                "The PIN is required for tax deduction",
                "It is a standard verification procedure"
            ),
            correctOptionIndex = 1,
            quizExplanation = "UPI PIN is only used to AUTHORIZE outflows of money. Receiving payments never requires entering your secret PIN."
        ),
        SecurityLesson(
            id = "LES-04",
            title = "OTP Theft & Social Engineering",
            category = "Identity Protection",
            iconName = "Lock",
            readTimeMinutes = 2,
            explanation = "One-Time Passwords (OTPs) provide the final barrier protecting your accounts. Attackers use social engineering and panic to trick you into reading them aloud.",
            warningSigns = listOf(
                "An unexpected call from 'Security Support' right after an OTP appears on your phone.",
                "The caller telling you they need the code to 'block an unauthorized debit'.",
                "Extreme urgency, background noise resembling a call center, or aggressive language."
            ),
            realisticScenario = "Your phone chimes with a 6-digit OTP for Amazon password reset. Seconds later, a caller claims: \"Someone in another state is buying a laptop with your card. Read me the 6 digits on your screen so I can freeze the order!\"",
            protectiveSteps = listOf(
                "NEVER share an OTP with ANYONE over the phone, chat, or email.",
                "Read the full OTP message carefully — it states what the code is actually authorizing.",
                "Hang up on unsolicited callers immediately.",
                "Enable app-based authenticator apps (TOTP) instead of SMS OTPs where possible."
            ),
            quizQuestion = "A caller claiming to be your bank's fraud team asks for the 6-digit code sent to your phone to cancel a fraudulent transaction. What should you do?",
            quizOptions = listOf(
                "Give them the code quickly before the transaction completes",
                "Refuse and hang up; real bank representatives never ask for OTPs",
                "Give them an incorrect code to test them",
                "Wait 5 minutes and then send it via SMS"
            ),
            correctOptionIndex = 1,
            quizExplanation = "Legitimate financial staff can cancel transactions from their internal system without ever needing your personal authorization OTP."
        ),
        SecurityLesson(
            id = "LES-05",
            title = "Lookalike Domains & Homoglyph Attacks",
            category = "Web Safety",
            iconName = "Language",
            readTimeMinutes = 2,
            explanation = "Cybercriminals register domain names that look indistinguishable from legitimate brands to trick you into entering credentials on an imposter site.",
            warningSigns = listOf(
                "Replacing letters with numbers (e.g., 'g00gle.com' or 'paypa1.com').",
                "Adding deceptive prefixes or suffixes (e.g., 'login-apple.com' or 'chase-security-alert.net').",
                "Punycode / Cyrillic characters that resemble Latin letters."
            ),
            realisticScenario = "You receive an email asking you to review an urgent DocuSign agreement. The link points to 'docuslgn.com' (with a lowercase 'L' instead of 'i'), leading to a fake Microsoft login screen.",
            protectiveSteps = listOf(
                "Look closely at the full domain name in the address bar, not just the page design.",
                "Check the domain name using Squish URL Scanner.",
                "Use a password manager; it will not auto-fill credentials on fake domains.",
                "Navigate directly to known websites by typing their verified URL."
            ),
            quizQuestion = "How does a password manager help protect against lookalike phishing domains?",
            quizOptions = listOf(
                "It automatically hacks and reports the malicious server",
                "It will only auto-fill credentials on the exact matching legitimate domain",
                "It changes your password to an unguessable phrase",
                "It deletes phishing emails automatically"
            ),
            correctOptionIndex = 1,
            quizExplanation = "Password managers verify the exact domain name in the browser and will refuse to auto-fill credentials on lookalike domains."
        )
    )

    fun getLessonById(id: String): SecurityLesson? = lessons.find { it.id == id }
}
