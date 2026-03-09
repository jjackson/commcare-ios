package org.commcare.modern.util

/**
 * A catch-all for centralizing hooks and state for tuning on performance optimizations which may
 * need to shift constants or boundaries based on the current platform
 *
 * Created by ctsims on 6/21/2017.
 */
object PerformanceTuningUtil {

    private var MAX_PREFETCH_CASE_BLOCK = -1

    private const val MB_64 = 64L * 1024 * 2014
    private const val MB_256 = 256L * 1024 * 2014
    private const val MB_1024 = 1024L * 1024 * 2014

    /**
     * Update the constant size of cases eligible for batch "pre-fetch" in db ops. This
     * is roughly the number of cases which can be expected to reliably fit into memory without
     * lowering the available heap sufficiently to introduce stability concerns.
     */
    @JvmStatic
    fun updateMaxPrefetchCaseBlock(newMaxSize: Int) {
        MAX_PREFETCH_CASE_BLOCK = newMaxSize
    }

    /**
     * @return A heuristic for the largest safe value for block prefetch based on the
     * current device runtime.
     */
    @JvmStatic
    fun guessLargestSupportedBulkCaseFetchSizeFromHeap(): Int {
        val rt = Runtime.getRuntime()
        val maxMemory = rt.maxMemory()
        return guessLargestSupportedBulkCaseFetchSizeFromHeap(maxMemory)
    }

    /**
     * @return A heuristic for the largest safe value for block prefetch based on a provided
     * amount of memory which should be available for optimizations.
     */
    @JvmStatic
    fun guessLargestSupportedBulkCaseFetchSizeFromHeap(availableMemoryInBytes: Long): Int {
        // NOTE: These are just tuned from experience and testing on mobile devices around values
        // which prevent them from running out of memory. It would be well worth it in the
        // future to take a more comprehensive approach.

        return when {
            availableMemoryInBytes == 0L || availableMemoryInBytes == -1L -> {
                // This was the existing tuned default, so don't change it until we have a better guess.
                7500
            }
            availableMemoryInBytes <= MB_64 -> 2500
            availableMemoryInBytes <= MB_256 -> 7500
            availableMemoryInBytes <= MB_1024 -> 15000
            else -> 50000
        }
    }

    /**
     * @return The maximum number of cases which will be included in a "Pre-Fetch Batch".
     */
    @JvmStatic
    fun getMaxPrefetchCaseBlock(): Int {
        if (MAX_PREFETCH_CASE_BLOCK == -1) {
            updateMaxPrefetchCaseBlock(guessLargestSupportedBulkCaseFetchSizeFromHeap())
        }
        return MAX_PREFETCH_CASE_BLOCK
    }
}
