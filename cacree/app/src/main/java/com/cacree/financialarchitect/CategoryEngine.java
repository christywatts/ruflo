package com.cacree.financialarchitect;

public class CategoryEngine {

    public static String classify(String lower, String type, String currency) {

        // ── INCOME ────────────────────────────────────────────────
        if ("income".equals(type)) {
            if (lower.contains("salary") || lower.contains("payroll") || lower.contains("mshahara")) return "Salary";
            if (lower.contains("remittance") || lower.contains("worldremit") ||
                lower.contains("western union") || lower.contains("wise") ||
                lower.contains("paypal")) return "Remittance";
            if (lower.contains("refund") || lower.contains("reimbursement")) return "Refund";
            return "Income";
        }

        // ── SMART EAST AFRICA MERCHANT INTENT ─────────────────────
        // These appear as "sent to" in wallet SMS, but they are not peer transfers.
        if (lower.contains("vodacom-bundles") || lower.contains("vodacom bundles") ||
            lower.contains("safaricom-bundles") || lower.contains("safaricom bundles") ||
            lower.contains("airtel-bundles") || lower.contains("airtel bundles") ||
            lower.contains("halotel-bundles") || lower.contains("halotel bundles") ||
            lower.contains("data bundles") || lower.contains("internet bundles"))
            return "Data Bundle";

        if (lower.contains("tips-airtelmoney") || lower.contains("tips airtelmoney") ||
            lower.contains("tips-selcom") || lower.contains("tips selcom") ||
            lower.contains("tips-halotel") || lower.contains("tips halotel") ||
            lower.contains("tips-mixx") || lower.contains("tips mixx") ||
            lower.contains("safaricom m-pesa tt") || lower.contains("safaricom mpesa tt") ||
            lower.contains("m-pesa tt") || lower.contains("mpesa tt"))
            return "Transport";

        if (lower.contains("luku") || lower.contains("tanesco") || lower.contains("kplc") ||
            lower.contains("umeme") || lower.contains("token"))
            return "Bills";

        if (lower.contains("bank debit") || lower.contains("cash withdrawal") ||
            lower.contains("bank withdrawal") || lower.contains("withdrawal -") ||
            lower.contains("umetoa") || lower.contains("imetolewa"))
            return "Withdrawal";

        // ── AIRTIME (separate from Telecom) ───────────────────────
        if (lower.contains("airtime") || lower.contains("top-up") || lower.contains("topup") ||
            lower.contains("recharge") || lower.contains("nunua muda") ||
            (lower.contains("umenunua") && lower.contains("dk")) || // Vodacom calling minutes
            lower.contains("calling credit"))
            return "Airtime";

        // ── DATA BUNDLES ──────────────────────────────────────────
        if (lower.contains("data bundle") || lower.contains("internet bundle") ||
            lower.contains("mb bundle") || lower.contains("gb bundle") ||
            (lower.contains("bundle") && (lower.contains("mb") || lower.contains("gb"))))
            return "Data Bundle";

        // ── DEBT / LOANS ──────────────────────────────────────────
        if (lower.contains("fuliza") || lower.contains("mshwari") || lower.contains("m-shwari") ||
            lower.contains("loan") || lower.contains("credit") || lower.contains("overdraft") ||
            lower.contains("borrow") || lower.contains("tala") || lower.contains("branch") ||
            lower.contains("okoa") || lower.contains("mwananchi"))
            return "Debt";

        // ── BILLS & UTILITIES ─────────────────────────────────────
        if (lower.contains("luku") || lower.contains("tanesco") || lower.contains("token") ||
            lower.contains("gepg") || lower.contains("dawasco") || lower.contains("maji") ||
            lower.contains("kplc") || lower.contains("electricity") || lower.contains("umeme") ||
            lower.contains("zuku") || lower.contains("dstv") || lower.contains("gotv") ||
            lower.contains("startimes") || lower.contains("paybill") || lower.contains("water") ||
            lower.contains("rent") || lower.contains("showmax") || lower.contains("netflix") ||
            lower.contains("wi-fi") || lower.contains("nwsc") || lower.contains("eskom") ||
            lower.contains("city power"))
            return "Bills";

        // ── GROCERIES ─────────────────────────────────────────────
        if (lower.contains("naivas") || lower.contains("carrefour") ||
            lower.contains("quickmart") || lower.contains("supermarket") ||
            lower.contains("grocery") || lower.contains("uchumi") ||
            lower.contains("pick n pay") || lower.contains("checkers") ||
            lower.contains("woolworths") || lower.contains("shoprite") ||
            lower.contains("spar") || lower.contains("game store"))
            return "Groceries";

        // ── DINING ───────────────────────────────────────────────
        if (lower.contains("java") || lower.contains("kfc") || lower.contains("pizza") ||
            lower.contains("restaurant") || lower.contains("cafe") || lower.contains("food") ||
            lower.contains("steers") || lower.contains("nando") || lower.contains("chicken inn") ||
            lower.contains("subway") || lower.contains("artcaffe") || lower.contains("debonairs"))
            return "Dining";

        // ── TRANSPORT ─────────────────────────────────────────────
        if (lower.contains("uber") || lower.contains("bolt") || lower.contains("matatu") ||
            lower.contains("fuel") || lower.contains("petrol") || lower.contains("diesel") ||
            lower.contains("parking") || lower.contains("boda") || lower.contains("indriver") ||
            lower.contains("minibus"))
            return "Transport";

        // ── PEER TRANSFERS ────────────────────────────────────────
        // M-Pesa TZ "[NAME] has received" = peer send
        if (lower.matches(".*[a-z]+ [a-z]+ has received.*")) return "Peer";
        if (lower.contains("sent to") || lower.contains("umetuma") ||
            lower.contains("kutoka kwa") ||  // received from peer
            lower.contains("transferred to") || lower.contains("send money"))
            return "Peer";

        // ── TELECOM (SMS/calls, not airtime/data) ─────────────────
        if (lower.contains("safaricom") || lower.contains("vodacom") ||
            lower.contains("airtel") || lower.contains("mtn") ||
            lower.contains("orange") || lower.contains("telkom") ||
            lower.contains("selcom") || lower.contains("tigo"))
            return "Telecom";

        // ── HEALTH ───────────────────────────────────────────────
        if (lower.contains("hospital") || lower.contains("pharmacy") ||
            lower.contains("clinic") || lower.contains("nhif") ||
            lower.contains("medical") || lower.contains("chemist"))
            return "Health";

        // ── EDUCATION ────────────────────────────────────────────
        if (lower.contains("school") || lower.contains("tuition") ||
            lower.contains("university") || lower.contains("college") ||
            lower.contains("fees"))
            return "Education";

        // ── WITHDRAWALS ───────────────────────────────────────────
        if (lower.contains("withdraw") || lower.contains("imetolewa") ||
            lower.contains("cash out") || lower.contains("atm") ||
            lower.contains("umetoa") || // NMB Swahili
            lower.contains("has been debited")) // ABSA TZ bank debit
            return "Withdrawal";

        // ── SAVINGS ──────────────────────────────────────────────
        if (lower.contains("savings") || lower.contains("akiba") ||
            lower.contains("deposit") || lower.contains("kimewekwa") ||
            lower.contains("investment"))
            return "Savings";

        // ── SHOPPING ─────────────────────────────────────────────
        if (lower.contains("jumia") || lower.contains("kilimall") ||
            lower.contains("amazon") || lower.contains("online") ||
            lower.contains("shopping") || lower.contains("store") ||
            lower.contains("google")) // Google Play purchases
            return "Shopping";

        return "Other";
    }
}
