package com.cacree.financialarchitect;

import java.util.*;

public class IntelligenceEngine {

    public static List<String> generate(Map<String, Double> m) {
        List<String> out = new ArrayList<>();

        double balance     = get(m, "balance");
        double income      = get(m, "income");
        double expense     = get(m, "expense");
        double debtRatio   = get(m, "debtRatio");
        double socialRatio = get(m, "socialRatio");
        double fixedLoad   = get(m, "fixedLoad");
        double savingsRate = get(m, "savingsRate");
        double burnRate    = get(m, "burnRate");
        double otherRatio  = get(m, "otherRatio");
        double score       = get(m, "score");
        int    txCount     = (int) get(m, "txCount");

        // ── CAPITAL STATE (weighted score) ────────────────────────
        String state;
        if      (score >= 0.6) state = "🔴 RISK";
        else if (score >= 0.3) state = "🟡 WARNING";
        else                   state = "🟢 STABLE";

        out.add("Capital State: " + state + " (score " + pct(score) + ")");

        // ── CASHFLOW ──────────────────────────────────────────────
        if (balance < 0)
            out.add("🔴 Negative cashflow → you spent more than you earned. Unsustainable.");
        else if (balance < income * 0.1)
            out.add("⚠️ Surplus is below 10% of income → fragile financial position.");

        // ── DEBT ─────────────────────────────────────────────────
        if (debtRatio > 0.40)
            out.add("⚠️ Debt consumes " + pct(debtRatio) + " of expenses → dominant financial drag.");
        else if (debtRatio > 0.25)
            out.add("⚠️ Debt at " + pct(debtRatio) + " of expenses → elevated pressure.");

        // ── FIXED LOAD ────────────────────────────────────────────
        if (fixedLoad > 0.60)
            out.add("🏋️ Fixed obligations consume " + pct(fixedLoad) + " of income → constrained cashflow.");

        // ── BURN RATE ─────────────────────────────────────────────
        if (burnRate > 0.90)
            out.add("🔥 Burn rate " + pct(burnRate) + " → less than " + pct(1-burnRate) + " retained.");

        // ── SOCIAL LEAKAGE ────────────────────────────────────────
        if (socialRatio > 0.25)
            out.add("👥 Peer transfers are " + pct(socialRatio) + " of spending → significant capital leak.");
        else if (socialRatio > 0.15)
            out.add("👥 Peer transfers at " + pct(socialRatio) + " → above the 15% sustainable threshold.");

        // ── UNCLASSIFIED SPENDING ─────────────────────────────────
        if (otherRatio > 0.40)
            out.add("❓ " + pct(otherRatio) + " of spending is unclassified → high noise in your data.");

        // ── SAVINGS ───────────────────────────────────────────────
        if (savingsRate >= 0.30)
            out.add("✅ Savings rate " + pct(savingsRate) + " → strong capital retention.");
        else if (savingsRate >= 0.20)
            out.add("✅ Savings rate " + pct(savingsRate) + " → sustainable. Architect-level discipline.");
        else if (savingsRate > 0 && savingsRate < 0.10)
            out.add("💡 Savings rate " + pct(savingsRate) + " → insufficient for capital accumulation.");

        // ── CLEAN STATE ───────────────────────────────────────────
        if (debtRatio == 0 && expense > 0)
            out.add("🎯 No debt detected → clean financial architecture.");

        // ── DATA VOLUME ───────────────────────────────────────────
        if (txCount < 5)
            out.add("📥 Insufficient data. Import SMS for complete behavioral analysis.");
        else if (txCount > 50)
            out.add("📊 " + txCount + " transactions analysed → pattern is statistically significant.");

        if (out.size() <= 1)
            out.add("📈 Financial state is stable. No anomalies detected.");

        return out;
    }

    public static String generateStory(Map<String, Double> m, String name, String month) {
        double income  = get(m, "income");
        double expense = get(m, "expense");
        double balance = income - expense;
        double savings = income > 0 ? balance / income : 0;
        double debt    = get(m, "debtRatio");
        int    txCount = (int) get(m, "txCount");

        StringBuilder sb = new StringBuilder();
        sb.append(name).append("'s ").append(month).append(" Financial Story\n\n");
        sb.append(txCount).append(" transactions. One narrative.\n\n");
        if (income > 0) sb.append("Earned ").append(fmt(income)).append(", spent ").append(fmt(expense)).append(".\n");
        if (balance > 0) {
            sb.append(fmt(balance)).append(" remained.\n");
            sb.append(savings >= 0.20
                ? "Top " + pct(savings) + " savings rate — you controlled your money.\n"
                : "There is room to retain more next month.\n");
        } else {
            sb.append("This month ran at a deficit. The architecture needs adjustment.\n");
        }
        if (debt > 0.30) sb.append("\nDebt was a dominant force in your cashflow.\n");
        sb.append("\nEvery shilling has a story. Cacree is keeping score.");
        return sb.toString();
    }

    private static double get(Map<String, Double> m, String k) {
        Double v = m.get(k); return v != null ? v : 0;
    }
    private static String pct(double v) { return Math.round(v * 100) + "%"; }
    private static String fmt(double v)  { return String.format("%,.0f", v); }
}
