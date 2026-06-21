package com.cacree.financialarchitect;

import java.text.SimpleDateFormat;
import java.util.*;

public class MetricsEngine {

    public static Map<String, Double> compute(List<Transaction> txs) {

        double income=0, expense=0, debt=0, peer=0, bills=0, dining=0, transport=0, telecom=0, other=0;

        Map<String, double[]> byCurrency = new LinkedHashMap<>();
        String[] CURRENCIES = {"KSh","TSh","UGX","ZAR","USD","GBP","EUR","AED","CAD","AUD"};
        for (String c : CURRENCIES) byCurrency.put(c, new double[]{0, 0});

        for (Transaction t : txs) {
            if (t == null) continue;
            String cat = t.category != null ? t.category : "Other";
            String cur = t.currency  != null ? t.currency  : "KSh";
            double[] cv = byCurrency.get(cur);
            if (cv == null) { cv = new double[]{0, 0}; byCurrency.put(cur, cv); }

            if ("income".equals(t.type) || "Salary".equals(cat) || "Income".equals(cat) || "Remittance".equals(cat)) {
                income += t.amount; cv[0] += t.amount;
            } else {
                expense += t.amount; cv[1] += t.amount;
                if ("Debt".equals(cat))      debt      += t.amount;
                if ("Peer".equals(cat))      peer      += t.amount;
                if ("Bills".equals(cat))     bills     += t.amount;
                if ("Dining".equals(cat))    dining    += t.amount;
                if ("Transport".equals(cat)) transport += t.amount;
                if ("Telecom".equals(cat))   telecom   += t.amount;
                if ("Other".equals(cat))     other     += t.amount;
            }
        }

        // Daily spend — NO STREAMS, use plain loop
        Map<String, Double> daily = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (Transaction t : txs) {
            if (!"expense".equals(t.type)) continue;
            String day = sdf.format(new Date(t.timestamp));
            Double prev = daily.get(day);
            daily.put(day, (prev != null ? prev : 0.0) + t.amount);
        }

        // Volatility — plain array loop, no streams
        double volatility = 0;
        if (!daily.isEmpty()) {
            double[] vals = new double[daily.size()];
            int idx = 0;
            for (Double v : daily.values()) vals[idx++] = v;
            double sum = 0;
            for (double v : vals) sum += v;
            double avg = sum / vals.length;
            double var = 0;
            for (double v : vals) var += (v - avg) * (v - avg);
            volatility = Math.sqrt(var / vals.length);
        }

        double balance     = income - expense;
        double savingsRate = income  > 0 ? balance / income          : 0;
        double debtRatio   = expense > 0 ? debt    / expense         : 0;
        double socialRatio = expense > 0 ? peer    / expense         : 0;
        double fixedLoad   = income  > 0 ? (debt + bills) / income   : 0;
        double burnRate    = income  > 0 ? expense / income          : 0;
        double otherRatio  = expense > 0 ? other   / expense         : 0;
        double normVol     = income  > 0 ? Math.min(volatility / income, 1.0) : 0;
        double score       = (debtRatio * 0.4) + (socialRatio * 0.3) + (normVol * 0.3);

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("income",      income);
        m.put("expense",     expense);
        m.put("balance",     balance);
        m.put("debt",        debt);
        m.put("peer",        peer);
        m.put("bills",       bills);
        m.put("dining",      dining);
        m.put("transport",   transport);
        m.put("telecom",     telecom);
        m.put("other",       other);
        m.put("savingsRate", savingsRate);
        m.put("debtRatio",   debtRatio);
        m.put("socialRatio", socialRatio);
        m.put("fixedLoad",   fixedLoad);
        m.put("burnRate",    burnRate);
        m.put("otherRatio",  otherRatio);
        m.put("volatility",  volatility);
        m.put("score",       score);
        m.put("txCount",     (double) txs.size());

        for (Map.Entry<String, double[]> e : byCurrency.entrySet()) {
            double[] cv = e.getValue();
            if (cv[0] + cv[1] > 0) {
                m.put("cur_" + e.getKey() + "_inc", cv[0]);
                m.put("cur_" + e.getKey() + "_exp", cv[1]);
                m.put("cur_" + e.getKey() + "_bal", cv[0] - cv[1]);
            }
        }
        return m;
    }
}
