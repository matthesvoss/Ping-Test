package de.matthesvoss.pingtest.util;

import java.util.Collections;
import java.util.PriorityQueue;

/**
 * Utility class that efficiently maintains a running median
 * for a stream of integer values using two heaps.
 */
public class MedianCalculator {
    // Max-heap for lower half
    private final PriorityQueue<Integer> low = new PriorityQueue<>(Collections.reverseOrder());
    // Min-heap for upper half
    private final PriorityQueue<Integer> high = new PriorityQueue<>();
    private int median = 0;

    /**
     * Adds a new value to the data structure and updates the median.
     */
    @SuppressWarnings("ConstantConditions")
    public void add(int value) {
        // Insert and rebalance
        if (low.isEmpty() || value <= low.peek()) {
            low.offer(value);
            if (low.size() > high.size() + 1) {
                high.offer(low.poll());
            }
        } else {
            high.offer(value);
            if (high.size() > low.size()) {
                low.offer(high.poll());
            }
        }

        // Compute median
        if (low.size() == high.size()) {
            median = (int) Math.round((low.peek() + high.peek()) / 2.0);
        } else {
            median = low.peek();
        }
    }

    /**
     * Returns the current median. Returns 0 if no values have been added.
     */
    public int getMedian() {
        return median;
    }

    /**
     * Clears all stored values.
     */
    public void clear() {
        low.clear();
        high.clear();
        median = 0;
    }

    /**
     * Returns the total number of values added.
     */
    public int size() {
        return low.size() + high.size();
    }
}
