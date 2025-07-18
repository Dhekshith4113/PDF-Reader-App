package com.example.pdfreader

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.core.widget.addTextChangedListener
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnOrientation: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvOpenFile: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var tvPageCount: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var viewPager: ViewPager2

    private val PICK_PDF_FILE = 2
    private var pdfRenderer: PdfRenderer? = null
    private var pdfAdapter: PdfPageAdapter? = null
    private var currentPageIndex = 0
    private var barsVisible = true
    private var totalPages: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val pdfUri = intent.data!!
            try {
                contentResolver.takePersistableUriPermission(pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.d("MainActivity", "${e.message}")
            }

            val uriHash = computeUriHash(this, pdfUri)
            if (uriHash != SharedPreferencesManager.loadUri(this)) {
                SharedPreferencesManager.setOnePageMode(this, true)
                SharedPreferencesManager.savePageNumber(this, 0)
                SharedPreferencesManager.setInvertEnabled(this, false)
                SharedPreferencesManager.setGrayscaleEnabled(this, false)
                SharedPreferencesManager.setSepiaEnabled(this, false)
                SharedPreferencesManager.setLandscapeOrientation(this, false)
            }

            if (SharedPreferencesManager.isLandscapeOrientation(this)) {
                btnOrientation.setImageResource(R.drawable.mobile_landscape_24)
            } else {
                btnOrientation.setImageResource(R.drawable.mobile_portrait_24)
            }

            if (SharedPreferencesManager.isOnePageMode(this)) {
                tvPageCount.text = "1"
            } else {
                tvPageCount.text = "2"
            }

            loadFile(pdfUri)
        }

    }

    private fun initViews() {
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        btnBack = findViewById(R.id.btnBack)
        btnMenu = findViewById(R.id.btnMenu)
        btnOrientation = findViewById(R.id.btnOrientation)
        tvTitle = findViewById(R.id.tvTitle)
        tvOpenFile = findViewById(R.id.tvOpenFile)
        tvPageCount = findViewById(R.id.tvPageCount)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        seekBar = findViewById(R.id.seekBar)
        viewPager = findViewById(R.id.viewPager)

        if (SharedPreferencesManager.isLandscapeOrientation(this)) {
            btnOrientation.setImageResource(R.drawable.mobile_landscape_24)
        } else {
            btnOrientation.setImageResource(R.drawable.mobile_portrait_24)
        }

        if (SharedPreferencesManager.isOnePageMode(this)) {
            tvPageCount.text = "1"
        } else {
            tvPageCount.text = "2"
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnMenu.setOnClickListener {
            showMenuDialog()
        }

        btnOrientation.setOnClickListener {
            if (SharedPreferencesManager.isLandscapeOrientation(this)) {
                SharedPreferencesManager.setLandscapeOrientation(this, false)
                btnOrientation.setImageResource(R.drawable.mobile_portrait_24)
            } else {
                SharedPreferencesManager.setLandscapeOrientation(this, true)
                btnOrientation.setImageResource(R.drawable.mobile_landscape_24)
            }
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            setupPdfViewer()
        }

        tvOpenFile.setOnClickListener {
            openFilePicker()
        }

        tvPageCount.setOnClickListener {
            if (SharedPreferencesManager.isOnePageMode(this)) {
                SharedPreferencesManager.setOnePageMode(this, false)
                tvPageCount.text = "2"
            } else {
                SharedPreferencesManager.setOnePageMode(this, true)
                tvPageCount.text = "1"
            }
            rememberPageNumber()
            setupPdfViewer()
        }

        tvPageIndicator.setOnClickListener {
            showJumpDialog()
        }

    }

    private fun showMenuDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_menu, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val tvOpenFile = dialogView.findViewById<TextView>(R.id.tvOpenFile)
        val btnLTR = dialogView.findViewById<RadioButton>(R.id.btnLTR)
        val btnRTL = dialogView.findViewById<RadioButton>(R.id.btnRTL)
        val btnOnePage = dialogView.findViewById<RadioButton>(R.id.btnOnePage)
        val btnTwoPage = dialogView.findViewById<RadioButton>(R.id.btnTwoPage)
        val switchLayout = dialogView.findViewById<LinearLayout>(R.id.switchLayout)
        val switchToggle = dialogView.findViewById<SwitchCompat>(R.id.switchToggle)
        val btnVertical = dialogView.findViewById<RadioButton>(R.id.btnVertical)
        val btnHorizontal = dialogView.findViewById<RadioButton>(R.id.btnHorizontal)
        val btnLow = dialogView.findViewById<RadioButton>(R.id.btnLow)
        val btnMedium = dialogView.findViewById<RadioButton>(R.id.btnMedium)
        val btnHigh = dialogView.findViewById<RadioButton>(R.id.btnHigh)
        val switchInvert = dialogView.findViewById<SwitchCompat>(R.id.switchInvert)
        val switchGrayscale = dialogView.findViewById<SwitchCompat>(R.id.switchGrayscale)
        val switchSepia = dialogView.findViewById<SwitchCompat>(R.id.switchSepia)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        btnLTR.isChecked = SharedPreferencesManager.isLeftToRightMode(this)
        btnRTL.isChecked = !SharedPreferencesManager.isLeftToRightMode(this)
        btnOnePage.isChecked = SharedPreferencesManager.isOnePageMode(this)
        btnTwoPage.isChecked = !SharedPreferencesManager.isOnePageMode(this)
        switchLayout.visibility = if (SharedPreferencesManager.isOnePageMode(this)) View.GONE else View.VISIBLE
        switchToggle.isChecked = SharedPreferencesManager.isCoverPageSeparate(this)
        btnVertical.isChecked = SharedPreferencesManager.isVerticalScrollMode(this)
        btnHorizontal.isChecked = !SharedPreferencesManager.isVerticalScrollMode(this)
        switchInvert.isChecked = SharedPreferencesManager.isInvertEnabled(this)
        switchGrayscale.isChecked = SharedPreferencesManager.isGrayscaleEnabled(this)
        switchSepia.isChecked = SharedPreferencesManager.isSepiaEnabled(this)

        if (SharedPreferencesManager.getResolution(this) == "LOW") {
            btnLow.isChecked = true
            btnMedium.isChecked = false
            btnHigh.isChecked = false
        } else if (SharedPreferencesManager.getResolution(this) == "MEDIUM") {
            btnLow.isChecked = false
            btnMedium.isChecked = true
            btnHigh.isChecked = false
        } else if (SharedPreferencesManager.getResolution(this) == "HIGH") {
            btnLow.isChecked = false
            btnMedium.isChecked = false
            btnHigh.isChecked = true
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        tvOpenFile.setOnClickListener {
            openFilePicker()
            dialog.dismiss()
        }

        btnLTR.setOnClickListener {
            btnLTR.isChecked = true
            btnRTL.isChecked = false
            SharedPreferencesManager.setLeftToRightMode(this, true)
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            setupPdfViewer()
        }

        btnRTL.setOnClickListener {
            btnLTR.isChecked = false
            btnRTL.isChecked = true
            SharedPreferencesManager.setLeftToRightMode(this, false)
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            setupPdfViewer()
        }

        btnOnePage.setOnClickListener {
            btnOnePage.isChecked = true
            btnTwoPage.isChecked = false
            SharedPreferencesManager.setOnePageMode(this, true)
            tvPageCount.text = "1"
            rememberPageNumber()
            setupPdfViewer()
            switchLayout.visibility = View.GONE
        }

        btnTwoPage.setOnClickListener {
            btnOnePage.isChecked = false
            btnTwoPage.isChecked = true
            SharedPreferencesManager.setOnePageMode(this, false)
            tvPageCount.text = "2"
            rememberPageNumber()
            setupPdfViewer()
            switchLayout.visibility = View.VISIBLE
        }

        switchToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                SharedPreferencesManager.setCoverPageSeparate(this, true)
            } else {
                SharedPreferencesManager.setCoverPageSeparate(this, false)
            }
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            setupPdfViewer()
        }

        btnVertical.setOnClickListener {
            btnVertical.isChecked = true
            btnHorizontal.isChecked = false
            SharedPreferencesManager.setVerticalScrollMode(this, true)
            setupPdfViewer()
        }

        btnHorizontal.setOnClickListener {
            btnVertical.isChecked = false
            btnHorizontal.isChecked = true
            SharedPreferencesManager.setVerticalScrollMode(this, false)
            setupPdfViewer()
        }

        btnLow.setOnClickListener {
            btnLow.isChecked = true
            btnMedium.isChecked = false
            btnHigh.isChecked = false
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setResolution(this, "LOW")
            setupPdfViewer()
        }

        btnMedium.setOnClickListener {
            btnLow.isChecked = false
            btnMedium.isChecked = true
            btnHigh.isChecked = false
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setResolution(this, "MEDIUM")
            setupPdfViewer()
        }

        btnHigh.setOnClickListener {
            btnLow.isChecked = false
            btnMedium.isChecked = false
            btnHigh.isChecked = true
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setResolution(this, "HIGH")
            setupPdfViewer()
        }

        switchInvert.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setInvertEnabled(this, isChecked)
            setupPdfViewer()
        }

        switchGrayscale.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setGrayscaleEnabled(this, isChecked)
            setupPdfViewer()
        }

        switchSepia.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.savePageNumber(this, viewPager.currentItem)
            Log.d("PageNumber", "Saved as: ${viewPager.currentItem}")
            SharedPreferencesManager.setSepiaEnabled(this, isChecked)
            setupPdfViewer()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showJumpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_jump_to_page, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val tvEnterPageNumber = dialogView.findViewById<TextView>(R.id.tvEnterPageNumber)
        val etPageNumber = dialogView.findViewById<EditText>(R.id.etPageNumber)
        val btnJump = dialogView.findViewById<Button>(R.id.btnJump)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        fun setButtonState(enabled: Boolean) {
            btnJump.isEnabled = enabled
            val textColor = if (enabled) R.color.background_color else R.color.text_color
            val backgroundColor = if (enabled) R.color.text_color else R.color.divider_grey

            btnJump.setTextColor(ContextCompat.getColor(this, textColor))
            btnJump.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, backgroundColor))
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        tvEnterPageNumber.text = "Enter page number (1 - $totalPages)"
        setButtonState(false)

        etPageNumber.addTextChangedListener {
            val input = it.toString().toIntOrNull()
            setButtonState(input != null && input in 1..totalPages)
        }

        btnJump.setOnClickListener {
            val page = etPageNumber.text.toString().toIntOrNull() ?: return@setOnClickListener
            val itemIndex = calculateViewPagerIndexFromPage(page)
            Log.d("PageNumber", "Jump to: $itemIndex")
            viewPager.setCurrentItem(itemIndex, false)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun calculateViewPagerIndexFromPage(page: Int): Int {
        return when {
            SharedPreferencesManager.isOnePageMode(this) -> page - 1
            SharedPreferencesManager.isCoverPageSeparate(this) -> page / 2
            page % 2 == 0 -> (page / 2) - 1
            else -> page / 2
        }
    }

    private fun rememberPageNumber() {
        val pageNumber = when {
            SharedPreferencesManager.isOnePageMode(this) -> {
                if (SharedPreferencesManager.isCoverPageSeparate(this)) (viewPager.currentItem * 2) - 1
                else if (viewPager.currentItem % 2 == 0) (viewPager.currentItem * 2) + 1
                else viewPager.currentItem * 2
            }
            SharedPreferencesManager.isCoverPageSeparate(this) -> {
                if (viewPager.currentItem % 2 == 0) viewPager.currentItem / 2
                else (viewPager.currentItem / 2) + 1
            }
            else -> viewPager.currentItem / 2
        }
        Log.d("PageNumber", "Saved: $pageNumber")
        SharedPreferencesManager.savePageNumber(this, pageNumber)
    }

    private fun updatePageIndicator(currentPage: Int) {
        val ltr = SharedPreferencesManager.isLeftToRightMode(this)
        val pageText = when {
            SharedPreferencesManager.isOnePageMode(this) ->
                if (ltr) "${currentPage + 1} / $totalPages" else "$totalPages / ${currentPage + 1}"

            SharedPreferencesManager.isCoverPageSeparate(this) -> {
                if (currentPage == 0) if (ltr) "1 / $totalPages" else "$totalPages / 1"
                else {
                    val first = currentPage * 2
                    val second = first + 1
                    val text = if (first == totalPages) "$first" else "$first - $second"
                    if (ltr) "$text / $totalPages" else "$totalPages / $text"
                }
            }

            else -> {
                val first = (currentPage * 2) + 1
                val second = first + 1
                val text = if (first == totalPages) "$first" else "$first - $second"
                if (ltr) "$text / $totalPages" else "$totalPages / $text"
            }
        }

        seekBar.progress = calculateSeekBarProgress(currentPage)
        tvPageIndicator.text = pageText
    }

    private fun calculateSeekBarProgress(currentPage: Int): Int {
        return when {
            SharedPreferencesManager.isOnePageMode(this) -> currentPage
            SharedPreferencesManager.isCoverPageSeparate(this) ->
                if (currentPage * 2 == totalPages) (currentPage * 2) - 1 else currentPage * 2
            else -> (currentPage * 2) + 1
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    private fun computeUriHash(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(inputStream.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadFile(uri: Uri) {
        try {
            val saveUri = computeUriHash(this, uri)
            SharedPreferencesManager.saveUri(this, saveUri)
            tvOpenFile.visibility = View.GONE
            viewPager.visibility = View.VISIBLE

            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor)
                totalPages = pdfRenderer!!.pageCount
                setupPdfViewer()

                // Update title with file name
                val fileName = getFileName(uri)
                tvTitle.text = fileName

                Toast.makeText(this, "PDF loaded successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "PDF Document"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Use default name
        }
        return fileName
    }

    private fun setupPdfViewer() {
        val renderer = pdfRenderer ?: return

        val isOnePageMode = SharedPreferencesManager.isOnePageMode(this)
        val isLandscapeMode = SharedPreferencesManager.isLandscapeOrientation(this)
        val isCoverPageSeparate = SharedPreferencesManager.isCoverPageSeparate(this)
        val isVerticalScroll = SharedPreferencesManager.isVerticalScrollMode(this)
        val isLeftToRight = SharedPreferencesManager.isLeftToRightMode(this)

        // Set ViewPager2 orientation
        viewPager.orientation = if (isVerticalScroll) {
            ViewPager2.ORIENTATION_VERTICAL
        } else {
            ViewPager2.ORIENTATION_HORIZONTAL
        }

        // Set layout direction
        viewPager.layoutDirection = if (isLeftToRight) {
            View.LAYOUT_DIRECTION_LTR
        } else {
            View.LAYOUT_DIRECTION_RTL
        }

        if (!isLandscapeMode) {
            val layoutParams = viewPager.layoutParams as ViewGroup.MarginLayoutParams
            // Set new width and height
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            viewPager.layoutParams = layoutParams
            viewPager.rotation = 0f
        } else {
            val layoutParams = viewPager.layoutParams as ViewGroup.MarginLayoutParams
            // Set new width and height
            val displayMetrics = Resources.getSystem().displayMetrics
            layoutParams.width = displayMetrics.heightPixels - 10.dpToPx(this)
            layoutParams.height = displayMetrics.widthPixels - 10.dpToPx(this)
            viewPager.layoutParams = layoutParams
            viewPager.rotation = 90f
        }

        seekBar.layoutDirection = viewPager.layoutDirection

        // Create adapter
        pdfAdapter = PdfPageAdapter(
            renderer,
            isOnePageMode,
            isCoverPageSeparate
        ) {
            Log.d("MainActivity", "Long Press detected - toggling bars")
            runOnUiThread {
                toggleBarsVisibility()
            }
        }

        viewPager.adapter = pdfAdapter

        // Setup page indicator and seekbar
        val currentPage = SharedPreferencesManager.loadPageNumber(this)
        Log.d("PageNumber", "Loaded as: $currentPage")

        viewPager.setCurrentItem(currentPage, false)
        seekBar.max = maxOf(0, totalPages - 1)
        updatePageIndicator(currentPage)

        // Setup ViewPager2 callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPageIndex = position
                updatePageIndicator(position)
            }
        })

        // Setup SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (SharedPreferencesManager.isOnePageMode(this@MainActivity)) {
                        viewPager.currentItem = progress
                    } else {
                        if (SharedPreferencesManager.isCoverPageSeparate(this@MainActivity)) {
                            viewPager.currentItem = (progress / 2) + 1
                        } else {
                            viewPager.currentItem = progress / 2
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private fun toggleBarsVisibility() {
        barsVisible = !barsVisible
        val visibility = if (barsVisible) View.VISIBLE else View.GONE

        topBar.visibility = visibility
        bottomBar.visibility = visibility

        // Animate the transition
        val fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val fadeOut = android.view.animation.AnimationUtils.loadAnimation(this, android.R.anim.fade_out)

        if (barsVisible) {
            topBar.startAnimation(fadeIn)
            bottomBar.startAnimation(fadeIn)
        } else {
            topBar.startAnimation(fadeOut)
            bottomBar.startAnimation(fadeOut)
        }
    }

    // Add this method to handle zoom state and disable ViewPager2 scrolling
    fun handleZoomState(isZoomed: Boolean) {
        viewPager.isUserInputEnabled = !isZoomed
    }

    // Add method to reset zoom for current page
    private fun resetCurrentPageZoom() {
        val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(viewPager.currentItem) as? PdfPageAdapter.PdfPageViewHolder
        viewHolder?.getZoomableImageView()?.resetZoom()
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                val uriHash = computeUriHash(this, uri)
                if (uriHash != SharedPreferencesManager.loadUri(this)) {
                    SharedPreferencesManager.setOnePageMode(this, true)
                    SharedPreferencesManager.savePageNumber(this, 0)
                    SharedPreferencesManager.setInvertEnabled(this, false)
                    SharedPreferencesManager.setGrayscaleEnabled(this, false)
                    SharedPreferencesManager.setSepiaEnabled(this, false)
                    SharedPreferencesManager.setLandscapeOrientation(this, false)
                }

                if (SharedPreferencesManager.isLandscapeOrientation(this)) {
                    btnOrientation.setImageResource(R.drawable.mobile_landscape_24)
                } else {
                    btnOrientation.setImageResource(R.drawable.mobile_portrait_24)
                }

                if (SharedPreferencesManager.isOnePageMode(this)) {
                    tvPageCount.text = "1"
                } else {
                    tvPageCount.text = "2"
                }

                loadFile(uri)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "Landscape mode", Toast.LENGTH_SHORT).show()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "Portrait mode", Toast.LENGTH_SHORT).show()
        }

    }

}