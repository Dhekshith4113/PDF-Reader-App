package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException

class PdfPageAdapter(
    private val pdfRenderer: PdfRenderer,
    private val isOnePageMode: Boolean,
    private val isCoverPageSeparate: Boolean,
    private val onLongPress: () -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PdfPageViewHolder(view, onLongPress)
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
        private val onLongPress: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val zoomableImageView: ZoomableImageView = itemView.findViewById(R.id.zoomableImageView)
        
        // Expose the ZoomableImageView for external access (e.g., reset zoom)
        fun getZoomableImageView(): ZoomableImageView = zoomableImageView

        init {
            zoomableImageView.setOnLongClickListener {
                Log.d("PdfPageAdapter", "Long press detected")
                onLongPress()
                true
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
            val bitmap = Bitmap.createBitmap(
                page.width * 4, // Increased from 2x to 4x for better resolution
                page.height * 4, // Increased from 2x to 4x for better resolution
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }

        private fun createDualPageBitmap(
            pdfRenderer: PdfRenderer,
            leftPageIndex: Int,
            rightPageIndex: Int
        ): Bitmap {
            val leftPage = if (leftPageIndex < pdfRenderer.pageCount) {
                pdfRenderer.openPage(leftPageIndex)
            } else null

            val rightPage = if (rightPageIndex < pdfRenderer.pageCount) {
                pdfRenderer.openPage(rightPageIndex)
            } else null

            val pageWidth = leftPage?.width ?: rightPage?.width ?: 0
            val pageHeight = leftPage?.height ?: rightPage?.height ?: 0

            val combinedBitmap = Bitmap.createBitmap(
                pageWidth * 8, // Increased from 4x to 8x for better resolution in dual page
                pageHeight * 4, // Increased from 2x to 4x for better resolution
                Bitmap.Config.ARGB_8888
            )

            val canvas = android.graphics.Canvas(combinedBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            leftPage?.let { page ->
                val leftBitmap = createBitmapFromPage(page)
                canvas.drawBitmap(leftBitmap, 0f, 0f, null)
                page.close()
            }

            rightPage?.let { page ->
                val rightBitmap = createBitmapFromPage(page)
                canvas.drawBitmap(rightBitmap, (pageWidth * 4).toFloat(), 0f, null) // Adjusted for 4x resolution
                page.close()
            }

            return combinedBitmap
        }
    }
}