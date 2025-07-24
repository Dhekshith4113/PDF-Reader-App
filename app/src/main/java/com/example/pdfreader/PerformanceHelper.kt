package com.example.pdfreader

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

object PerformanceHelper {
    
    private const val MAX_BITMAP_POOL_SIZE = 15
    private const val MEMORY_WARNING_THRESHOLD = 0.85f
    
    // Global bitmap pool for memory reuse
    private val globalBitmapPool = ConcurrentLinkedQueue<Bitmap>()
    
    /**
     * Check if device is running low on memory
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableMemory = memoryInfo.availMem
        val totalMemory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            memoryInfo.totalMem
        } else {
            Runtime.getRuntime().totalMemory()
        }
        
        val usedMemoryRatio = 1.0f - (availableMemory.toFloat() / totalMemory.toFloat())
        return usedMemoryRatio > MEMORY_WARNING_THRESHOLD
    }
    
    /**
     * Get recommended resolution based on available memory
     */
    fun getRecommendedResolution(context: Context, preferredResolution: String): String {
        if (isLowMemory(context)) {
            return when (preferredResolution) {
                "HIGH" -> "MEDIUM"
                "MEDIUM" -> "LOW"
                else -> "LOW"
            }
        }
        return preferredResolution
    }
    
    /**
     * Try to get a reusable bitmap from the pool
     */
    fun tryGetReusableBitmap(width: Int, height: Int): Bitmap? {
        val iterator = globalBitmapPool.iterator()
        while (iterator.hasNext()) {
            val bitmap = iterator.next()
            if (bitmap.width == width && 
                bitmap.height == height && 
                !bitmap.isRecycled) {
                iterator.remove()
                return bitmap
            }
        }
        return null
    }
    
    /**
     * Return bitmap to pool for reuse
     */
    fun returnBitmapToPool(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled && globalBitmapPool.size < MAX_BITMAP_POOL_SIZE) {
                globalBitmapPool.offer(it)
            }
        }
    }
    
    /**
     * Clear bitmap pool when memory is low
     */
    fun clearBitmapPool() {
        if (BuildConfig.DEBUG_MODE) {
            Log.d("PerformanceHelper", "Clearing bitmap pool, size: ${globalBitmapPool.size}")
        }
        globalBitmapPool.clear()
    }
    
    /**
     * Force garbage collection (use sparingly)
     */
    fun forceGarbageCollection() {
        if (BuildConfig.DEBUG_MODE) {
            Log.d("PerformanceHelper", "Forcing garbage collection")
        }
        System.gc()
    }
    
    /**
     * Get memory usage info for debugging
     */
    fun getMemoryInfo(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        return "Used: ${usedMemory / (1024 * 1024)}MB, " +
               "Max: ${maxMemory / (1024 * 1024)}MB, " +
               "Available: ${memoryInfo.availMem / (1024 * 1024)}MB, " +
               "Pool size: ${globalBitmapPool.size}"
    }
    
    /**
     * Calculate optimal bitmap configuration based on device capabilities
     */
    fun getOptimalBitmapConfig(): Bitmap.Config {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap.Config.RGBA_F16 // Better quality on newer devices
        } else {
            Bitmap.Config.ARGB_8888
        }
    }
    
    /**
     * Check if device supports hardware acceleration
     */
    fun isHardwareAccelerated(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try {
                val activity = context as? android.app.Activity
                activity?.window?.attributes?.flags?.and(
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                ) != 0
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
}