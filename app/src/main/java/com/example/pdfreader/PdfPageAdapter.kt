package com.example.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorInvertFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import java.util.concurrent.ConcurrentLinkedQueue

class PdfPageAdapter(
    private val pdfRenderer: PdfRenderer,
    private val isOnePageMode: Boolean,
    private val isCoverPageSeparate: Boolean,
    private val onSingleTap: () -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

    // Bitmap pool for memory reuse
    private val bitmapPool = ConcurrentLinkedQueue<Bitmap>()
    private val maxPoolSize = 10
    
    // GPU Image instance reuse
    private var gpuImage: GPUImage? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        
        // Initialize shared GPU Image instance
        if (gpuImage == null) {
            gpuImage = GPUImage(parent.context)
        }
        
        return PdfPageViewHolder(view, onSingleTap, bitmapPool, gpuImage!!)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        if (isOnePageMode) {
            holder.bindSinglePage(pdfRenderer, position)
        } else {
            holder.bindDualPage(pdfRenderer, position, isCoverPageSeparate)
        }
    }

    override fun getItemCount(): Int {
        return if (isOnePageMode) {
            pdfRenderer.pageCount
        } else {
            if (isCoverPageSeparate) {
                // First page separate, then pairs
                1 + ((pdfRenderer.pageCount - 1) + 1) / 2
            } else {
                // All pages in pairs
                (pdfRenderer.pageCount + 1) / 2
            }
        }
    }
    
    override fun onViewRecycled(holder: PdfPageViewHolder) {
        super.onViewRecycled(holder)
        holder.recycleBitmap()
    }

    class PdfPageViewHolder(
        itemView: View,
        private val onSingleTap: () -> Unit,
        private val bitmapPool: ConcurrentLinkedQueue<Bitmap>,
        private val gpuImage: GPUImage
    ) : RecyclerView.ViewHolder(itemView) {

        private val zoomableImageView: ZoomableImageView = itemView.findViewById(R.id.zoomableImageView)
        private val resLow: Int = 2
        private val resMedium: Int = 3
        private val resHigh: Int = 4
        
        private var currentBitmap: Bitmap? = null

        // Expose the ZoomableImageView for external access (e.g., reset zoom)
        fun getZoomableImageView(): ZoomableImageView = zoomableImageView

        init {
            zoomableImageView.setOnSingleTapToggleListener {
                onSingleTap()
            }

            zoomableImageView.setOnZoomChangeListener { isZoomed ->
                (itemView.context as? MainActivity)?.handleZoomState(isZoomed)
            }
        }
        
        fun recycleBitmap() {
            currentBitmap?.let { bitmap ->
                // Return to global pool for better memory management
                PerformanceHelper.returnBitmapToPool(bitmap)
                
                // Also try local pool if global is full
                if (!bitmap.isRecycled && bitmapPool.size < 10) {
                    bitmapPool.offer(bitmap)
                }
            }
            currentBitmap = null
        }

        fun bindSinglePage(pdfRenderer: PdfRenderer, position: Int) {
            val page = pdfRenderer.openPage(position)
            val bitmap = createBitmapFromPage(page)
            currentBitmap = bitmap
            zoomableImageView.setImageBitmap(bitmap)
            page.close()
        }

        fun bindDualPage(pdfRenderer: PdfRenderer, position: Int, isCoverPageSeparate: Boolean) {
            if (isCoverPageSeparate && position == 0) {
                // First page alone
                val page = pdfRenderer.openPage(0)
                val bitmap = createBitmapFromPage(page)
                currentBitmap = bitmap
                zoomableImageView.setImageBitmap(bitmap)
                page.close()
            } else {
                // Calculate page indices for dual view
                val startPageIndex = if (isCoverPageSeparate) {
                    1 + (position - 1) * 2
                } else {
                    position * 2
                }

                if (SharedPreferencesManager.isLeftToRightMode(zoomableImageView.context)) {
                    val leftPageIndex = startPageIndex
                    val rightPageIndex = startPageIndex + 1

                    val bitmap = createDualPageBitmap(pdfRenderer, leftPageIndex, rightPageIndex)
                    currentBitmap = bitmap
                    zoomableImageView.setImageBitmap(bitmap)
                } else {
                    val leftPageIndex = startPageIndex + 1
                    val rightPageIndex = startPageIndex

                    val bitmap = createDualPageBitmap(pdfRenderer, leftPageIndex, rightPageIndex)
                    currentBitmap = bitmap
                    zoomableImageView.setImageBitmap(bitmap)
                }
            }
        }

        private fun createBitmapFromPage(page: PdfRenderer.Page): Bitmap {
            val context = zoomableImageView.context
            val preferredResolution = SharedPreferencesManager.getResolution(context) ?: "LOW"
            val resolution = when (PerformanceHelper.getRecommendedResolution(context, preferredResolution)) {
                "LOW" -> resLow
                "MEDIUM" -> resMedium
                else -> resHigh
            }

            // Try to reuse bitmap from pool
            val targetWidth = page.width * resolution
            val targetHeight = page.height * resolution
            
            var bitmap = PerformanceHelper.tryGetReusableBitmap(targetWidth, targetHeight)
            if (bitmap == null) {
                // Use optimal bitmap config for better performance
                bitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    PerformanceHelper.getOptimalBitmapConfig()
                )
            }

            // Create white background directly on the bitmap
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
            
            // Render PDF page directly to the bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Apply filters efficiently (combine filters to minimize bitmap copies)
            return applyFiltersOptimized(bitmap, context)
        }
        
        private fun getReusableBitmap(width: Int, height: Int): Bitmap? {
            val iterator = bitmapPool.iterator()
            while (iterator.hasNext()) {
                val pooledBitmap = iterator.next()
                if (pooledBitmap.width == width && 
                    pooledBitmap.height == height && 
                    !pooledBitmap.isRecycled) {
                    iterator.remove()
                    return pooledBitmap
                }
            }
            return null
        }

        private fun applyFiltersOptimized(bitmap: Bitmap, context: Context): Bitmap {
            val needsGrayscale = SharedPreferencesManager.isGrayscaleEnabled(context)
            val needsInvert = SharedPreferencesManager.isInvertEnabled(context)
            
            // If no filters needed, return original
            if (!needsGrayscale && !needsInvert) {
                return bitmap
            }
            
            // Apply all needed filters in one pass using GPU
            gpuImage.setImage(bitmap)
            
            when {
                needsGrayscale && needsInvert -> {
                    // Create combined filter if both needed
                    val grayscaleFilter = GPUImageGrayscaleFilter()
                    val invertFilter = GPUImageColorInvertFilter()
                    
                    gpuImage.setFilter(grayscaleFilter)
                    val intermediateBitmap = gpuImage.bitmapWithFilterApplied
                    
                    gpuImage.setImage(intermediateBitmap)
                    gpuImage.setFilter(invertFilter)
                    return gpuImage.bitmapWithFilterApplied
                }
                needsGrayscale -> {
                    gpuImage.setFilter(GPUImageGrayscaleFilter())
                    return gpuImage.bitmapWithFilterApplied
                }
                needsInvert -> {
                    gpuImage.setFilter(GPUImageColorInvertFilter())
                    return gpuImage.bitmapWithFilterApplied
                }
                else -> return bitmap
            }
        }

        private fun createDualPageBitmap(
            pdfRenderer: PdfRenderer,
            leftPageIndex: Int,
            rightPageIndex: Int
        ): Bitmap? {
            val resolution = when (SharedPreferencesManager.getResolution(zoomableImageView.context)) {
                "LOW" -> resLow
                "MEDIUM" -> resMedium
                else -> resHigh
            }

            val leftPage = if (leftPageIndex < pdfRenderer.pageCount) {
                pdfRenderer.openPage(leftPageIndex)
            } else null

            val rightPage = if (rightPageIndex < pdfRenderer.pageCount) {
                pdfRenderer.openPage(rightPageIndex)
            } else null

            // Return null if both pages are missing (nothing to draw)
            if (leftPage == null && rightPage == null) return null

            val leftWidth = leftPage?.width ?: 0
            val rightWidth = rightPage?.width ?: 0
            val leftHeight = leftPage?.height ?: 0
            val rightHeight = rightPage?.height ?: 0

            val totalWidth = (leftWidth + rightWidth) * resolution
            val maxHeight = maxOf(leftHeight, rightHeight) * resolution

            // Try to reuse bitmap from pool
            var combinedBitmap = PerformanceHelper.tryGetReusableBitmap(totalWidth, maxHeight)
            if (combinedBitmap == null) {
                combinedBitmap = Bitmap.createBitmap(
                    totalWidth,
                    maxHeight,
                    PerformanceHelper.getOptimalBitmapConfig()
                )
            }

            val canvas = Canvas(combinedBitmap)

            // Set transparent background efficiently
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            var currentX = 0f

            leftPage?.let { page ->
                val leftBitmap = createBitmapFromPage(page)
                canvas.drawBitmap(leftBitmap, currentX, 0f, null)
                currentX += leftBitmap.width.toFloat()
                page.close()
            }

            rightPage?.let { page ->
                val rightBitmap = createBitmapFromPage(page)
                canvas.drawBitmap(rightBitmap, currentX, 0f, null)
                page.close()
            }

            return combinedBitmap
        }
    }
}