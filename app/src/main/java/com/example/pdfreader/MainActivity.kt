package com.example.pdfreader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnZoomReset: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvOpenFile: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var viewPager: ViewPager2

    private val PICK_PDF_FILE = 2
    private var pdfRenderer: PdfRenderer? = null
    private var pdfAdapter: PdfPageAdapter? = null
    private var currentPageIndex = 0
    private var barsVisible = true

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
                contentResolver.takePersistableUriPermission(pdfUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            loadFile(pdfUri)
        }
    }

    private fun initViews() {
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        btnBack = findViewById(R.id.btnBack)
        btnMenu = findViewById(R.id.btnMenu)
        btnZoomReset = findViewById(R.id.btnZoomReset)
        tvTitle = findViewById(R.id.tvTitle)
        tvOpenFile = findViewById(R.id.tvOpenFile)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        seekBar = findViewById(R.id.seekBar)
        viewPager = findViewById(R.id.viewPager)

        btnBack.setOnClickListener {
            finish()
        }

        btnMenu.setOnClickListener {
            showMenuDialog()
        }

        btnZoomReset.setOnClickListener {
            resetCurrentPageZoom()
        }

        tvOpenFile.setOnClickListener {
            openFilePicker()
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
        val switchView = dialogView.findViewById<View>(R.id.switchView)
        val switchLayout = dialogView.findViewById<LinearLayout>(R.id.switchLayout)
        val switchToggle = dialogView.findViewById<SwitchCompat>(R.id.switchToggle)
        val btnVertical = dialogView.findViewById<RadioButton>(R.id.btnVertical)
        val btnHorizontal = dialogView.findViewById<RadioButton>(R.id.btnHorizontal)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        btnLTR.isChecked = SharedPreferencesManager.isLeftToRightMode(this)
        btnRTL.isChecked = !SharedPreferencesManager.isLeftToRightMode(this)
        btnOnePage.isChecked = SharedPreferencesManager.isOnePageMode(this)
        btnTwoPage.isChecked = !SharedPreferencesManager.isOnePageMode(this)
        switchView.visibility = if (SharedPreferencesManager.isOnePageMode(this)) View.GONE else View.VISIBLE
        switchLayout.visibility = if (SharedPreferencesManager.isOnePageMode(this)) View.GONE else View.VISIBLE
        switchToggle.isChecked = SharedPreferencesManager.isCoverPageSeparate(this)
        btnVertical.isChecked = SharedPreferencesManager.isVerticalScrollMode(this)
        btnHorizontal.isChecked = !SharedPreferencesManager.isVerticalScrollMode(this)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        tvOpenFile.setOnClickListener {
            openFilePicker()
        }

        btnLTR.setOnClickListener {
            btnLTR.isChecked = true
            btnRTL.isChecked = false
            SharedPreferencesManager.setLeftToRightMode(this, true)
        }

        btnRTL.setOnClickListener {
            btnLTR.isChecked = false
            btnRTL.isChecked = true
            SharedPreferencesManager.setLeftToRightMode(this, false)
        }

        btnOnePage.setOnClickListener {
            btnOnePage.isChecked = true
            btnTwoPage.isChecked = false
            SharedPreferencesManager.setOnePageMode(this, true)

            switchView.visibility = View.GONE
            switchLayout.visibility = View.GONE
        }

        btnTwoPage.setOnClickListener {
            btnOnePage.isChecked = false
            btnTwoPage.isChecked = true
            SharedPreferencesManager.setOnePageMode(this, false)

            switchView.visibility = View.VISIBLE
            switchLayout.visibility = View.VISIBLE
        }

        switchToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                SharedPreferencesManager.setCoverPageSeparate(this, true)
            } else {
                SharedPreferencesManager.setCoverPageSeparate(this, false)
            }
        }

        btnVertical.setOnClickListener {
            btnVertical.isChecked = true
            btnHorizontal.isChecked = false
            SharedPreferencesManager.setVerticalScrollMode(this, true)
        }

        btnHorizontal.setOnClickListener {
            btnVertical.isChecked = false
            btnHorizontal.isChecked = true
            SharedPreferencesManager.setVerticalScrollMode(this, false)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            setupPdfViewer()
        }

        dialog.show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    private fun loadFile(uri: Uri) {
        try {
            tvOpenFile.visibility = View.GONE
            viewPager.visibility = View.VISIBLE

            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor)
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

        // Create adapter
        pdfAdapter = PdfPageAdapter(
            renderer,
            isOnePageMode,
            isCoverPageSeparate
        ) {
            Log.d("MainActivity", "Long press detected - toggling bars")
            runOnUiThread {
                toggleBarsVisibility()
            }
        }

        viewPager.adapter = pdfAdapter

        // Setup page indicator and seekbar
        val totalPages = pdfAdapter?.itemCount ?: 0
        updatePageIndicator(0, totalPages)

        seekBar.max = maxOf(0, totalPages - 1)
        seekBar.progress = 0

        // Setup ViewPager2 callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPageIndex = position
                updatePageIndicator(position, totalPages)
                seekBar.progress = position
            }
        })

        // Setup SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewPager.currentItem = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        tvPageIndicator.text = "${currentPage + 1} / $totalPages"
    }

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
                loadFile(uri)
            }
        }
    }

}