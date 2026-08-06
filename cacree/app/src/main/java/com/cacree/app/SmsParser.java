package com.cacree.app;

import java.security.MessageDigest;
import java.util.regex.*;

public class SmsParser {

    public static final String KES = "KSh";
    public static final String TZS = "TSh";
    public static final String UGX = "UGX";
    public static final String ZAR = "ZAR";
    public static final String USD = "USD";
    public static final String GBP = "GBP";
    public static final String EUR = "EUR";
    public static final String AED = "AED";

    public static Transaction parse(String body, String address, long date) {
        if (body == null || body.trim().isEmpty()) return null;
        String lower = body.toLowerCase();

        // ── SKIP DECLINED / FAILED TRANSACTIONS ─────────────────
        if (lower.contains("declined") || lower.contains("unsuccessful") ||
            lower.contains("failed") || lower.contains("reversed back") ||
            lower.contains("insufficient funds")) {
            return null;
        }

        // ── SKIP PROMOTIONAL SMS ─────────────────────────────────
        if (lower.contains("bonyeza") && lower.contains("http") && !lower.contains("confirmed") ||
            lower.contains("nirahisifu") || lower.contains("#nirahisifu") ||
            (lower.contains("bure") && lower.contains("http") && !containsAmount(lower))) {
            return null;
        }

        // ── 1. DETECT CURRENCY ───────────────────────────────────
        String currency = detectCurrency(lower, address);
        if (currency == null) return null;

        // ── 2. DETECT PROVIDER ───────────────────────────────────
        String provider = detectProvider(lower, currency);

        // ── 3. STRIP BALANCE BEFORE AMOUNT EXTRACTION ───────────
        String stripped = stripBalance(body, lower);

        // ── 4. EXTRACT AMOUNT ────────────────────────────────────
        double amount = extractAmount(stripped, currency);
        if (amount <= 0 || amount > 500_000_000) return null;

        // ── 5. DETERMINE TYPE ────────────────────────────────────
        String type = detectType(lower);

        // ── 6. EXTRACT TITLE ─────────────────────────────────────
        String title = extractTitle(body, lower, type, provider);

        // ── 7. CLASSIFY CATEGORY ─────────────────────────────────
        String category = CategoryEngine.classify(lower, type, currency);

        // ── 8. DEDUP HASH ─────────────────────────────────────────
        String hash = sha256(body.trim());

        Transaction t = new Transaction(amount, type, category, address, hash, date);
        t.title    = title;
        t.currency = currency;
        t.source   = provider;
        return t;
    }

    private static boolean containsAmount(String lower) {
        return lower.matches(".*\\d{3,}.*");
    }

    // ── CURRENCY DETECTION ────────────────────────────────────────
    private static String detectCurrency(String lower, String address) {
        String addr = address == null ? "" : address.toLowerCase();

        // ── 0. EXPLICIT CODES WIN FIRST (most reliable) ──────────
        if (lower.contains("tzs") || lower.contains("tsh")) return TZS;
        if (lower.contains("ksh") || lower.contains(" kes") || lower.contains("kes ")) return KES;
        if (lower.contains("ugx") || lower.contains("ug shs") || lower.contains("ush")) return UGX;
        if (lower.contains("rwf")) return "RWF";
        if (lower.contains("zar") || lower.contains(" rand")) return ZAR;

        // ── 1. SENDER ADDRESS decides country for ambiguous M-Pesa ──
        // Tanzania senders
        if (addr.contains("vodacom") || addr.contains("vodacomtz") || addr.contains("tigo") ||
            addr.contains("halotel") || addr.contains("halopesa") || addr.contains("ttcl") ||
            addr.contains("airteltz") || addr.contains("yas") || addr.contains("nmb") ||
            addr.contains("crdb") || addr.contains("selcom") || addr.contains("azampesa") ||
            addr.contains("nbc")) return TZS;
        // Kenya senders
        if (addr.contains("safaricom") || (addr.contains("mpesa") && !addr.contains("vodacom")) ||
            addr.contains("equity") || addr.contains("kcb") || addr.contains("ncba") ||
            addr.contains("fuliza") || addr.contains("co-op") || addr.contains("absa kenya")) {
            // only if message has no TZ-only Swahili markers
            if (!lower.contains("umetuma") && !lower.contains("umepokea") && !lower.contains("umetoa"))
                return KES;
        }
        // Uganda / SA senders
        if (addr.contains("mtn") || addr.contains("airtelug") || addr.contains("stanbic")) return UGX;
        if (addr.contains("fnb") || addr.contains("capitec") || addr.contains("nedbank")) return ZAR;

        // ── 2. TANZANIA Swahili-language markers (M-Pesa TZ uses Swahili) ──
        if (lower.contains("umetuma") || lower.contains("umepokea") || lower.contains("umetoa") ||
            lower.contains("imetolewa") || lower.contains("kimewekwa") ||
            lower.contains("kutoka akaunti") || lower.contains("akaunti yako") ||
            lower.contains("salio") || lower.contains("kiasi cha") || lower.contains("umelipa"))
            return TZS;
        // Selcom / Tigo / NMB / CRDB providers (TZ)
        if (lower.contains("selcom") || lower.contains("tigopesa") || lower.contains("tigo pesa") ||
            lower.contains("halopesa") || lower.contains("t-pesa") ||
            lower.contains("nmb") || lower.contains("crdb") || lower.contains("azampesa"))
            return TZS;
        // ABSA Tanzania debit/credit pattern
        if (lower.contains("transaction of tzs") || lower.contains("transaction of tsh")) return TZS;

        // ── 3. KENYA provider/loan markers (Kenya-only products) ──
        if (lower.contains("fuliza") || lower.contains("mshwari") || lower.contains("m-shwari") ||
            lower.contains("safaricom") || lower.contains("equity bank") ||
            lower.contains("co-operative bank") || lower.contains("ncba") ||
            (lower.contains("kcb") && !lower.contains("tzs")))
            return KES;

        // ── 4. UGANDA / SOUTH AFRICA providers ───────────────────
        if (lower.contains("mtn mobile money") || lower.contains("mtn momo") ||
            lower.contains("stanbic") || lower.contains("dfcu") || lower.contains("centenary"))
            return UGX;
        if (lower.contains("fnb") || lower.contains("nedbank") || lower.contains("capitec") ||
            lower.contains("standard bank") || lower.contains("snapscan"))
            return ZAR;

        // ── 5. EXPAT CURRENCIES ──────────────────────────────────
        if (lower.contains("usd") || lower.contains("$ ") || lower.contains("dollars")) return USD;
        if (lower.contains("gbp") || lower.contains("£"))   return GBP;
        if (lower.contains("eur") || lower.contains("€"))   return EUR;
        if (lower.contains("aed") || lower.contains("dirhams")) return AED;

        // ── 6. LAST RESORT ───────────────────────────────────────
        // Generic M-Pesa with no other signal: default to Tanzania (this app's primary market)
        if (lower.contains("m-pesa") || lower.contains("mpesa")) return TZS;

        return null;
    }

    // ── PROVIDER DETECTION ────────────────────────────────────────
    private static String detectProvider(String lower, String currency) {
        // Tanzania
        if (lower.contains("selcom"))              return "Selcom Pesa";
        if (lower.contains("tigopesa") || lower.contains("tigo pesa")) return "Tigo Pesa";
        if (lower.contains("halopesa"))            return "HaloPesa";
        if (lower.contains("nmb"))                 return "NMB Bank";
        if (lower.contains("crdb"))                return "CRDB";
        if (lower.contains("absa") && TZS.equals(currency)) return "ABSA Tanzania";
        if (lower.contains("t-pesa") || lower.contains("tpesa")) return "T-Pesa";
        // Kenya
        if (lower.contains("fuliza"))              return "Fuliza";
        if (lower.contains("mshwari") || lower.contains("m-shwari")) return "M-Shwari";
        if (lower.contains("kcb"))                 return "KCB";
        if (lower.contains("equity"))              return "Equity Bank";
        if (lower.contains("absa") && KES.equals(currency)) return "ABSA Kenya";
        if (lower.contains("co-operative"))        return "Co-op Bank";
        if ((lower.contains("mpesa") || lower.contains("m-pesa")) && KES.equals(currency)) return "M-Pesa";
        if ((lower.contains("mpesa") || lower.contains("m-pesa")) && TZS.equals(currency)) return "M-Pesa TZ";
        if (lower.contains("airtel") && KES.equals(currency)) return "Airtel Kenya";
        if (lower.contains("airtel") && TZS.equals(currency)) return "Airtel Tanzania";
        // Uganda
        if (lower.contains("mtn"))                 return "MTN MoMo";
        if (lower.contains("stanbic"))             return "Stanbic Uganda";
        if (lower.contains("dfcu"))                return "DFCU";
        if (lower.contains("centenary"))           return "Centenary Bank";
        // SA
        if (lower.contains("fnb"))                 return "FNB";
        if (lower.contains("nedbank"))             return "Nedbank";
        if (lower.contains("capitec"))             return "Capitec";
        if (lower.contains("standard bank"))       return "Standard Bank";
        if (lower.contains("absa") && ZAR.equals(currency)) return "ABSA SA";
        // Expat
        if (lower.contains("wise") || lower.contains("transferwise")) return "Wise";
        if (lower.contains("western union"))       return "Western Union";
        if (lower.contains("worldremit"))          return "WorldRemit";
        if (lower.contains("paypal"))              return "PayPal";

        return currency.equals(TZS) ? "Mobile Money TZ" :
               currency.equals(KES) ? "M-Pesa" :
               currency.equals(UGX) ? "Mobile Money UG" :
               currency.equals(ZAR) ? "Bank SA" : "Transfer";
    }

    // ── BALANCE STRIPPING ─────────────────────────────────────────
    private static String stripBalance(String body, String lower) {
        String s = body;
        // Strip from "balance is now" / "bal is now" / "balance"
        String[] balPatterns = {
            "(?i)your\\s+bal(?:ance)?\\s+is\\s+now.*",
            "(?i)balance\\s+is\\s+(?:now\\s+)?(?:ksh|tsh|tzs|ugx|zar)",
            "(?i)new\\s+m-?pesa\\s+balance.*",
            "(?i)available\\s+balance.*",
            "(?i)account\\s+balance.*",
            "(?i)a/c\\s+balance.*",
            "(?i)excluding\\s+hold.*"
        };
        for (String p : balPatterns) {
            s = s.replaceAll(p, "");
        }
        // Also strip everything after "excluding"
        int exIdx = s.toLowerCase().indexOf("excluding");
        if (exIdx > 10) s = s.substring(0, exIdx);
        return s;
    }

    // ── AMOUNT EXTRACTION ─────────────────────────────────────────
    private static double extractAmount(String text, String currency) {
        try {
            String lower = text.toLowerCase();
            String[] patterns;

            if (TZS.equals(currency)) {
                patterns = new String[]{
                    // "transaction of TZS 40,000.00"
                    "transaction\\s+of\\s+(?:tzs|tsh)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
                    // "Tsh4,000.00" or "Tsh 4,000"
                    "(?:tzs|tsh)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
                    // "TZS 10,000.00 kutoka"
                    "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s+(?:kutoka|kwa|sent|received|umepokea|umetoa)"
                };
            } else if (KES.equals(currency)) {
                patterns = new String[]{
                    "(?:ksh|kes)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
                };
            } else if (UGX.equals(currency)) {
                patterns = new String[]{
                    "(?:ugx|ug\\s*shs?)\\.?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
                };
            } else if (ZAR.equals(currency)) {
                patterns = new String[]{
                    "(?:zar|r)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
                };
            } else {
                patterns = new String[]{
                    "(?:usd|gbp|eur|aed)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
                    "[£$€]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
                };
            }

            for (String pat : patterns) {
                Matcher m = Pattern.compile(pat, Pattern.CASE_INSENSITIVE).matcher(text);
                if (m.find()) {
                    double v = Double.parseDouble(m.group(1).replace(",", ""));
                    if (v > 0) return v;
                }
            }

            // Fallback: largest formatted number in text
            Matcher m = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?)").matcher(text);
            double best = 0;
            while (m.find()) {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                if (v > best) best = v;
            }
            if (best > 0) return best;

            // Last resort: any standalone number > 100
            m = Pattern.compile("\\b([0-9]{4,})\\b").matcher(text);
            while (m.find()) {
                double v = Double.parseDouble(m.group(1));
                if (v > 100) return v;
            }
        } catch (Exception e) { android.util.Log.e("Cacree", "extractAmount: " + e.getMessage()); }
        return 0;
    }

    // ── TYPE DETECTION ────────────────────────────────────────────
    private static String detectType(String lower) {

        // ── EXPENSE — check FIRST, most specific ─────────────────
        // ABSA TZ: "has been debited to your ACC"
        if (lower.contains("has been debited")) return "expense";
        // M-Pesa TZ: "FIRSTNAME LASTNAME has received" = YOU sent = expense
        if (lower.matches(".*[a-z]+ [a-z]+ has received.*")) return "expense";
        // Swahili: Umetoa = you withdrew/sent
        if (lower.contains("umetoa")) return "expense";
        // Sent to someone
        if (lower.contains("sent to") || lower.contains("umetuma") || lower.contains("transferred to")) return "expense";
        // Withdrawal
        if (lower.contains("withdraw") || lower.contains("imetolewa") || lower.contains("cash out")) return "expense";
        // Bills
        if (lower.contains("paid to") || lower.contains("payment to") || lower.contains("paybill") ||
            lower.contains("buy goods") || lower.contains("lipa")) return "expense";
        // Airtime purchase
        if (lower.contains("airtime") && lower.contains("purchased")) return "expense";
        // Selcom debit: "TZS X kutoka kwa" = from someone = income (handled below)
        // But: "kutoka akaunti yako" = from your account = expense
        if (lower.contains("kutoka akaunti yako")) return "expense";
        // M-Pesa TZ: "Tsh X sent to"
        if (lower.matches(".*tsh[0-9,. ]+sent to.*")) return "expense";

        // ── INCOME ───────────────────────────────────────────────
        // ABSA TZ: "has been credited"
        if (lower.contains("has been credited")) return "income";
        // Swahili: Umepokea = you received, kimewekwa = was deposited to you
        if (lower.contains("umepokea") || lower.contains("kimewekwa")) return "income";
        // Selcom: "kutoka kwa NAME" = someone sent to you
        if (lower.contains("kutoka kwa") && !lower.contains("kutoka akaunti")) return "income";
        // NMB: deposited to your account
        if (lower.contains("kwenye akaunti yako") && !lower.contains("umetoa")) return "income";
        if (lower.contains("you have received") || lower.contains("you received") ||
            lower.contains("received from") || lower.contains("money received")) return "income";
        if (lower.contains("credited") || lower.contains("deposited") || lower.contains("imewekwa")) return "income";
        if (lower.contains("salary") || lower.contains("mshahara")) return "income";
        if (lower.contains("refund") || lower.contains("reimbursement")) return "income";
        // M-Pesa TZ confirmed receive: confirmation code + received but not "[name] has received"
        if (lower.contains("confirmed") && lower.contains("received") &&
            !lower.matches(".*[a-z]+ [a-z]+ has received.*")) return "income";

        return "expense"; // safe default
    }

    // ── TITLE EXTRACTION ─────────────────────────────────────────
    private static String extractTitle(String body, String lower, String type, String provider) {
        Matcher m;

        if ("income".equals(type)) {
            if (lower.contains("salary") || lower.contains("mshahara")) return "Salary";
            if (lower.contains("refund"))  return "Refund";

            // Selcom: "TZS 10,000.00 kutoka kwa FREDERICK NGOIYA"
            m = Pattern.compile("kutoka kwa\\s+([A-Za-z][A-Za-z ]+?)(?:\\s*[-–]|\\s*\\(|\\s*tarehe|\\s*\\d|$)",
                Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) return "From " + titleCase(m.group(1).trim());

            // NMB: "kimewekwa kwenye akaunti yako" — no name, just say deposit
            if (lower.contains("kimewekwa") || lower.contains("kwenye akaunti yako")) return "Bank Deposit";

            // M-Pesa: "received from JOHN"
            m = Pattern.compile("(?:received from|from)\\s+([A-Za-z][A-Za-z0-9 .'-]{1,35}?)\\s+(?:on|via|\\d)",
                Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) return "From " + titleCase(m.group(1).trim());

            // M-Pesa TZ confirmed: credit
            if (lower.contains("confirmed") && lower.contains("received")) {
                m = Pattern.compile("confirmed\\.?\\s+([A-Z][A-Z ]+?)\\s+has\\s+received",
                    Pattern.CASE_INSENSITIVE).matcher(body);
                if (m.find()) return "From " + titleCase(m.group(1).trim());
            }

            return "Money Received";
        }

        // EXPENSE
        // ABSA Tanzania: "A transaction of TZS X has been debited"
        if (lower.contains("has been debited") && lower.contains("acc")) return "Bank Debit";

        // M-Pesa TZ "[NAME] has received"
        m = Pattern.compile("^[A-Z0-9]+\\s+Confirmed\\.\\s+([A-Z][A-Z ]+?)\\s+has\\s+received",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) return "Sent to " + titleCase(m.group(1).trim());

        // M-Pesa TZ "Tsh4,000.00 sent to TIPS-Mixx By Yas"
        m = Pattern.compile("(?:tsh|tzs)[0-9,\\.]+\\s+sent to\\s+([A-Za-z0-9][A-Za-z0-9 &'.,-]{1,40}?)\\s+(?:for|on|\\d|$)",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) return "Sent to " + titleCase(m.group(1).trim());

        // NMB: "Umetoa TZS X kutoka akaunti yako" — withdrawal
        if (lower.contains("umetoa") && lower.contains("kutoka akaunti")) return "Bank Withdrawal";

        // Withdraw from agent
        m = Pattern.compile("Withdraw.*?from\\s+\\d+\\s*[-–]\\s*([A-Z][A-Z ]+?)(?:\\s+Total|\\s+fee|$)",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) return "Withdrawal - " + titleCase(m.group(1).trim());

        // "sent to NAME" — \b guards against "conSENT TO sharing your medical information..."
        m = Pattern.compile("\\b(?:sent to|umetuma kwa|umetuma)\\s+([A-Za-z][A-Za-z0-9 .'-]{1,35}?)(?:\\s+0?[0-9]|\\.|,|for|$)",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) return "Sent to " + titleCase(m.group(1).trim());

        // "paid to NAME"
        m = Pattern.compile("paid to\\s+([A-Za-z0-9][A-Za-z0-9 &',.-]{1,40}?)(?:\\.|,|\\s+via|\\s+new|$)",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) return titleCase(m.group(1).trim());

        // Known merchants
        String[][] merchants = {
            {"kplc","KPLC Electricity"},{"naivas","Naivas"},{"carrefour","Carrefour"},
            {"quickmart","Quickmart"},{"zuku","Zuku Internet"},{"dstv","DSTV"},
            {"java","Java House"},{"kfc","KFC"},{"uber","Uber"},{"bolt","Bolt"},
            {"airtime","Airtime"},{"bundle","Data Bundle"},{"data bundle","Data Bundle"},
            {"tips-mixx","TIPS-Mixx"},{"google","Google"},
            {"pick n pay","Pick n Pay"},{"checkers","Checkers"},{"shoprite","Shoprite"},
            {"capitec","Capitec Loan"},{"paybill","Bill Payment"},
            {"withdraw","Cash Withdrawal"},{"fuliza","Fuliza Loan"},{"mshwari","M-Shwari"}
        };
        for (String[] pair : merchants) {
            if (lower.contains(pair[0])) return pair[1];
        }

        return provider != null ? provider + " Transaction" : "Transaction";
    }

    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return sb.toString();
    }

    static String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString().substring(0, 32);
        } catch (Exception e) { return String.valueOf(Math.abs(text.hashCode())); }
    }
}
