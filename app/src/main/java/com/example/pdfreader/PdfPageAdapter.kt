package com.example.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorInvertFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter

class PdfPageAdapter(
    private val pdfRenderer: PdfRenderer,
    private val isOnePageMode: Boolean,
    private val isCoverPageSeparate: Boolean,
    private val onSingleTap: () -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PdfPageViewHolder(view, onSingleTap)
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

    class PdfPageViewHolder(
        itemView: View,
        private val onSingleTap: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val zoomableImageView: ZoomableImageView = itemView.findViewById(R.id.zoomableImageView)
        private val resLow: Int = 2
        private val resMedium: Int = 3
        private val resHigh: Int = 4

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

        fun bindSinglePage(pdfRenderer: PdfRenderer, position: Int) {
            val page = pdfRenderer.openPage(position)
            val bitmap = createBitmapFromPage(page)
            zoomableImageView.setImageBitmap(bitmap)
            page.close()
        }

        fun bindDualPage(pdfRenderer: PdfRenderer, position: Int, isCoverPageSeparate: Boolean) {
            if (isCoverPageSeparate && position == 0) {
                // First page alone
                val page = pdfRenderer.openPage(0)
                val bitmap = createBitmapFromPage(page)
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
                    zoomableImageView.setImageBitmap(bitmap)
                } else {
                    val leftPageIndex = startPageIndex + 1
                    val rightPageIndex = startPageIndex

                    val bitmap = createDualPageBitmap(pdfRenderer, leftPageIndex, rightPageIndex)
                    zoomableImageView.setImageBitmap(bitmap)
                }
            }
        }

        private fun createBitmapFromPage(page: PdfRenderer.Page): Bitmap {
            val context = zoomableImageView.context
            val resolution = when (SharedPreferencesManager.getResolution(context)) {
                "LOW" -> resLow
                "MEDIUM" -> resMedium
                else -> resHigh
            }

            val bitmap = Bitmap.createBitmap(
                page.width * resolution,
                page.height * resolution,
                Bitmap.Config.ARGB_8888
            )

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            var processedBitmap = applyGpuSharpenFilter(bitmap, context)

            if (SharedPreferencesManager.isGrayscaleEnabled(context)) {
                processedBitmap = applyGrayscaleFilter(processedBitmap, context)
            }

            if (SharedPreferencesManager.isInvertEnabled(context)) {
                processedBitmap = applyInvertFilter(processedBitmap, context)
            }

            return processedBitmap
        }

        private fun applyGpuSharpenFilter(bitmap: Bitmap, context: Context): Bitmap {
            val gpuImage = GPUImage(context)
            gpuImage.setImage(bitmap) // load image
            gpuImage.setFilter(GPUImageSharpenFilter(1.0f)) // 0.0f (no sharpen) to ~4.0f (strong sharpen)
            return gpuImage.bitmapWithFilterApplied
        }

        private fun applyGrayscaleFilter(bitmap: Bitmap, context: Context): Bitmap {
            val gpuImage = GPUImage(context)
            gpuImage.setImage(bitmap) // load image
            gpuImage.setFilter(GPUImageGrayscaleFilter())
            return gpuImage.bitmapWithFilterApplied
        }

        private fun applyInvertFilter(bitmap: Bitmap, context: Context): Bitmap {
            val gpuImage = GPUImage(context)
            gpuImage.setImage(bitmap) // load image
            gpuImage.setFilter(GPUImageColorInvertFilter())
            return gpuImage.bitmapWithFilterApplied
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

            val combinedBitmap = Bitmap.createBitmap(
                totalWidth,
                maxHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(combinedBitmap)

            // Optional: set transparent background instead of white
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