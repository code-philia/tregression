package com.github.myxxxsquared.gpt_invoker;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Usage {
    private static final Map<String, double[]> GPT_PRICE_TABLE = Map.of(
            "default", new double[] { 5, 20, 2.5 },
            "gpt-4.1", new double[] { 2, 8, 0.5 },
            "gpt-4.1-mini", new double[] { 0.4, 1.6, 0.1 },
            "gpt-4.1-nano", new double[] { 0.1, 0.4, 0.025 },
            "gpt-4o", new double[] { 2.5, 10, 1.25 },
            "gpt-4o-mini", new double[] { 0.15, 0.6, 0.075 });

    private final String modelName;
    private final ReentrantLock lock = new ReentrantLock();
    private long promptTokens = 0, completionTokens = 0, promptTokensCached = 0, completionTokensCached = 0;

    public Usage(String modelName) {
        if (!GPT_PRICE_TABLE.containsKey(modelName)) {
            System.err.println("Model " + modelName + " not in price table, using default prices.");
            modelName = "default";
        }
        this.modelName = modelName;
    }

    public void clear() {
        lock.lock();
        try {
            promptTokens = completionTokens = promptTokensCached = completionTokensCached = 0;
        } finally {
            lock.unlock();
        }
    }

    public void update(long prompt, long completion, boolean isCached) {
        lock.lock();
        try {
            if (isCached) {
                promptTokensCached += prompt;
                completionTokensCached += completion;
            } else {
                promptTokens += prompt;
                completionTokens += completion;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        double[] price = GPT_PRICE_TABLE.get(modelName);
        double inPrice = promptTokens * price[0] / 1e6;
        double outPrice = completionTokens * price[1] / 1e6;
        double inCached = promptTokensCached * price[0] / 1e6;
        double outCached = completionTokensCached * price[1] / 1e6;
        long totalTokens = promptTokens + completionTokens + promptTokensCached + completionTokensCached;
        double totalPrice = inPrice + outPrice + inCached + outCached;

        return String.format(
                "%s Input: %d tokens (%.6f USD), Output: %d tokens (%.6f USD), " +
                        "Cached Input: %d tokens (%.6f USD), Cached Output: %d tokens (%.6f USD), Total: %d tokens (%.6f USD)",
                modelName.equals("default") ? "(Estimated by GPT-4o prices)" : "",
                promptTokens, inPrice,
                completionTokens, outPrice,
                promptTokensCached, inCached,
                completionTokensCached, outCached,
                totalTokens, totalPrice);
    }
}