package com.google.firebase.ml.md.kotlin

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import com.google.firebase.ml.md.R
import com.google.firebase.ml.md.kotlin.EntityModels.ProductData.Tile
import com.google.firebase.ml.md.kotlin.EntityModels.ProductOrder.Order
import com.google.firebase.ml.md.kotlin.EntityModels.ProductOrder.OrderDetail
import com.google.firebase.ml.md.kotlin.Models.Service.ProductData.SelectProductTileData
import com.google.firebase.ml.md.kotlin.Models.Service.ProductOrder.InsertProductOrder
import com.google.firebase.ml.md.kotlin.OtherProduct.OtherProductTileAdapter
import com.google.firebase.ml.md.kotlin.camera.CameraSource
import com.google.firebase.ml.md.kotlin.camera.CameraSourcePreview
import com.google.firebase.ml.md.kotlin.camera.GraphicOverlay
import com.google.firebase.ml.md.kotlin.camera.WorkflowModel
import com.google.firebase.ml.md.kotlin.objectdetection.MultiObjectProcessor
import com.google.firebase.ml.md.kotlin.objectdetection.ProminentObjectProcessor
import com.google.firebase.ml.md.kotlin.productSearch.BottomSheetScrimView
import com.google.firebase.ml.md.kotlin.productSearch.SearchEngine
import com.google.firebase.ml.md.kotlin.settings.PreferenceUtils
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.tile_layout.view.*
import java.io.IOException
import java.text.NumberFormat

class LiveObjectDetectionFragment : Fragment(), View.OnClickListener {
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    //    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var searchButton: ExtendedFloatingActionButton? = null
    private var searchButtonAnimator: AnimatorSet? = null
    private var searchProgressBar: ProgressBar? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null
    private var searchEngine: SearchEngine? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var productRecyclerView: RecyclerView? = null
    private var bottomSheetTitleView: TextView? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false
    private var mainCustomLayout: LinearLayout? = null
    private var cartBtn: ImageButton? = null
    private var historyScanBtn: Button? = null
    private var historyOrderBtn: Button? = null
    private var logoutBtn: Button? = null
    private var addCart: Button? = null
    private var removeCart: Button? = null
    var testUI: TextView? = null
    var testUI1: TextView? = null
    var pref: SharedPreferences? = null
    var v: View? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater.inflate(R.layout.activity_live_object_kotlin, container, false)

        pref = this.activity!!.getSharedPreferences("SP_USER_DATA", Context.MODE_PRIVATE)




        searchEngine = SearchEngine(this.context!!)
        workflowModel = null
//        setContentView(R.layout.activity_live_object_kotlin)
        preview = v!!.findViewById(R.id.camera_preview)

        testUI = v!!.findViewById(R.id.card_title)
        testUI1 = v!!.findViewById(R.id.card_subtitle)

        mainCustomLayout = v!!.findViewById(R.id.mainCustomLayout)
        graphicOverlay = v!!.findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@LiveObjectDetectionFragment)
            cameraSource = CameraSource(this)
        }
        promptChip = v!!.findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
                (AnimatorInflater.loadAnimator(this.context, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                    setTarget(promptChip)
                }
        searchButton = v!!.findViewById<ExtendedFloatingActionButton>(R.id.product_search_button).apply {
            setOnClickListener(this@LiveObjectDetectionFragment)
        }
        searchButtonAnimator =
                (AnimatorInflater.loadAnimator(this.context, R.animator.search_button_enter) as AnimatorSet).apply {
                    setTarget(searchButton)
                }
        searchProgressBar = v!!.findViewById(R.id.search_progress_bar)
        setUpBottomSheet()

//        v!!.findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = v!!.findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@LiveObjectDetectionFragment)
        }
//        settingsButton = v!!.findViewById<View>(R.id.settings_button).apply {
//            setOnClickListener(this@LiveObjectDetectionFragment)
//        }
//        cartBtn = v!!.findViewById<ImageButton>(R.id.cartBtn).apply {
//            setOnClickListener(this@LiveObjectDetectionFragment)
//        }

//        historyScanBtn = v!!.findViewById<Button>(R.id.historyScanBtn).apply {
//            setOnClickListener(this@LiveObjectDetectionFragment)
//        }
//
//        historyOrderBtn = v!!.findViewById<Button>(R.id.historyOrderBtn).apply {
//            setOnClickListener(this@LiveObjectDetectionFragment)
//        }
//        logoutBtn = v!!.findViewById<Button>(R.id.logoutBtn).apply {
//            setOnClickListener(this@LiveObjectDetectionFragment)
//        }

        setUpWorkflowModel()

        return v
    }

    override fun onResume() {
        super.onResume()

        if (!Utils.allPermissionsGranted(this.context!!)) {
            Utils.requestRuntimePermissions(this.activity!!)
        }

        workflowModel?.markCameraFrozen()
//        settingsButton?.isEnabled = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(
                if (PreferenceUtils.isMultipleObjectsMode(this.context!!)) {
                    MultiObjectProcessor(graphicOverlay!!, workflowModel!!)
                } else {
                    ProminentObjectProcessor(graphicOverlay!!, workflowModel!!)
                }
        )
        workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
        searchEngine?.shutdown()
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(v!!.findViewById(R.id.bottom_sheet))
        bottomSheetBehavior?.setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
//                        Log.d(LiveObjectDetectionActivity.TAG, "Bottom sheet new state: $newState")
                        bottomSheetScrimView?.visibility =
                                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                    View.GONE
                                } else View.VISIBLE


//                        if (newState == BottomSheetBehavior.STATE_DRAGGING) {
//                            bottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
//                        }


                        graphicOverlay?.clear()

                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
                            BottomSheetBehavior.STATE_COLLAPSED,
                            BottomSheetBehavior.STATE_EXPANDED,
                            BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false
                            BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        val searchedObject = workflowModel!!.searchedObject.value
                        if (searchedObject == null || java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val graphicOverlay = graphicOverlay ?: return
                        val bottomSheetBehavior = bottomSheetBehavior ?: return
                        val collapsedStateHeight = bottomSheetBehavior.peekHeight.coerceAtMost(bottomSheet.height)
                        val bottomBitmap = objectThumbnailForBottomSheet ?: return
                        if (slidingSheetUpFromHiddenState) {
                            val thumbnailSrcRect = graphicOverlay.translateRect(searchedObject.boundingBox)
                            bottomSheetScrimView?.updateWithThumbnailTranslateAndScale(
                                    bottomBitmap,
                                    collapsedStateHeight,
                                    slideOffset,
                                    thumbnailSrcRect)
                        } else {
                            bottomSheetScrimView?.updateWithThumbnailTranslate(
                                    bottomBitmap, collapsedStateHeight, slideOffset, bottomSheet)
                        }
                    }
                })

        bottomSheetScrimView = v!!.findViewById<BottomSheetScrimView>(R.id.bottom_sheet_scrim_view).apply {
            setOnClickListener(this@LiveObjectDetectionFragment)
        }

        bottomSheetTitleView = v!!.findViewById(R.id.bottom_sheet_title)
//        productRecyclerView = findViewById<RecyclerView>(R.id.product_recycler_view).apply {
//            setHasFixedSize(true)
//            layoutManager = LinearLayoutManager(this@LiveObjectDetectionActivity)
//            adapter = ProductAdapter(ImmutableList.of())
//        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java).apply {

            // Observes the workflow state changes, if happens, update the overlay view indicators and
            // camera preview state.

            workflowState.observe(this@LiveObjectDetectionFragment, Observer { workflowState ->
                if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                    return@Observer
                }
                currentWorkflowState = workflowState
                Log.d("LogBoom2", "Current workflow state: ${workflowState.name}")

                if (PreferenceUtils.isAutoSearchEnabled(this@LiveObjectDetectionFragment.context!!)) {
                    stateChangeInAutoSearchMode(workflowState)
                } else {
                    stateChangeInManualSearchMode(workflowState)
                }
            })


            // Observes changes on the object to search, if happens, fire product search request.
            objectToSearch!!.observe(this@LiveObjectDetectionFragment, Observer { detectObject ->
                searchEngine!!.search(detectObject) { detectedObject, productTest ->
                    workflowModel?.onSearchCompleted(detectedObject, productTest)
                }
            })


            // Observes changes on the object that has search completed, if happens, show the bottom sheet
            // to present search result.
            searchedObject.observe(this@LiveObjectDetectionFragment, Observer { nullableSearchedObject ->
                val searchedObject = nullableSearchedObject ?: return@Observer
                val productList = searchedObject.productList

                objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
//                bottomSheetTitleView?.text = resources
//                        .getQuantityString(
//                                R.plurals.bottom_sheet_title, productList.size, productList.size)
//                bottomSheetTitleView?.text = "Boom Test Text"

//                productRecyclerView?.adapter = ProductAdapter(productList)

//                card_title.text = searchedObject.productList.text1
//                card_subtitle.text = searchedObject.productList.text2
//                  testUI!!.text = searchedObject.productList.text1
//                  testUI1?.text = searchedObject.productList.text2


                if (searchedObject.productList != null) {
                    setData(searchedObject.productList)

                    slidingSheetUpFromHiddenState = true
                    bottomSheetBehavior?.peekHeight =
                            preview?.height?.div(1) ?: BottomSheetBehavior.PEEK_HEIGHT_AUTO
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
//                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

                } else {
                    Toast.makeText(this@LiveObjectDetectionFragment.context, "Not Found!!", Toast.LENGTH_SHORT).show()
                    workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
                }


            })
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowModel.WorkflowState) {
        val wasPromptChipGone = promptChip?.visibility == View.GONE
        val wasSearchButtonGone = searchButton?.visibility == View.GONE

        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_point_at_an_object)
                searchButton?.visibility = View.GONE
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = true
                searchButton?.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = false
                searchButton?.setBackgroundColor(Color.GRAY)
                searchProgressBar!!.visibility = View.VISIBLE
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
                stopCameraPreview()
            }
            else -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        promptChipAnimator?.let {
            if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
        }

        val shouldPlaySearchButtonEnteringAnimation = wasSearchButtonGone && searchButton?.visibility == View.VISIBLE
        searchButtonAnimator?.let {
            if (shouldPlaySearchButtonEnteringAnimation && !it.isRunning) it.start()
        }
    }

    public fun setData(productData: Any?) {

        mainCustomLayout?.removeAllViews()
        if (productData is Tile) {
            var inflater: LayoutInflater = LayoutInflater.from(this.context)
            var wizardView = inflater!!.inflate(R.layout.tile_layout, mainCustomLayout, false)
            mainCustomLayout?.addView(wizardView)

            var imageView: ImageView = v!!.findViewById(R.id.tile_image)
            var amount: Int = 1
            Picasso.get().load(productData.tileImage).into(imageView)
//            wizardView.product_image.setImageResource(productData.imageResource)
//            wizardView.food_and_bev_image.setImageResource(R.drawable.coke_no_sugar)
            wizardView.tile_brand.text = productData.tileBrand
            wizardView.tile_model.text = productData.tileModel
            wizardView.tile_price.text = "฿" + NumberFormat.getInstance().format(productData.tilePrice!! * amount).toString()
            wizardView.tile_spec.text = "KgsPerCtn: " + productData.tileKgsPerCtn.toString() + "\nSquareMeterPerCtn: " +
                    productData.tileSquareMeterPerCtn.toString() + "\nSquareFTPerCtn: " + productData.tileSquareFTPerCtn.toString() +
                    "\nQuantity: " + productData.tileQuantity.toString() + "\nSize (cm.): " + productData.tileSize.toString()


            wizardView.tile_increase.setOnClickListener {
                amount++
                wizardView.amount.text = amount.toString()
                wizardView.tile_price.text = "฿" + NumberFormat.getInstance().format(productData.tilePrice!! * amount).toString()
            }
            wizardView.tile_decrease.setOnClickListener {
                if (amount > 1) amount--
                wizardView.amount.text = amount.toString()
                wizardView.tile_price.text = "฿" + NumberFormat.getInstance().format(productData.tilePrice!! * amount).toString()
            }
            wizardView.tile_close.setOnClickListener {
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            }

            wizardView.tile_add_to_cart.setOnClickListener {
                var order = Order(null, pref!!.getString("UUID", ""), null, null,
                        null, null, arrayOf(OrderDetail(null, null, productData.tileId,
                        null, amount)).toList())

                val listenerPostOrder = object : InsertProductOrder.getDataComplete {
                    override fun getDataComplete(jsonString: String) {
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }

                InsertProductOrder(listenerPostOrder, order).execute(IPAddress.ipAddress + "product-order/insertProductOrder/")
            }
            val urlSelectData = IPAddress.ipAddress + "product-data/selectOtherProductTile/${productData.tileId}"
            val listenerSelectData = object : SelectProductTileData.getDataComplete {
                override fun getDataComplete(tileList: List<Tile>) {

                    var tileL = ArrayList<Tile>()
                    var random = ArrayList<Int>()
                    var loop = true


                    while (loop) {
                        if (random.size <= 5) {
                            val rand = (0..(tileList.size - 1)).random()
                            var same = false
                            if (random.size == 0) {
                                random.add(rand)
                            } else {
                                for (i in 0..(random.size - 1)) {
                                    if (rand == random[i]) {
                                        same = true
                                    }
                                }
                                if (!same) {
                                    random.add(rand)
                                }
                            }


                        } else {
                            loop = false
                        }


                    }

                    for (i in 0..(random.size - 1)) {
                        tileL.add(tileList[random[i]])
                    }
//                    tileL.add(tileList[0])
//                    tileL.add(tileList[1])
//                    tileL.add(tileList[0])
//                    tileL.add(tileList[1])
//                    tileL.add(tileList[0])
//                    tileL.add(tileList[1])

                    wizardView.otherProductRecycleView.apply {
                        layoutManager = LinearLayoutManager(this@LiveObjectDetectionFragment.context, LinearLayoutManager.HORIZONTAL, false)
//                        adapter = OtherProductFoodAndBevAdapter(foodAndBevList)
                        adapter = OtherProductTileAdapter(tileL.toList(), this@LiveObjectDetectionFragment)
                    }

                }

            }

            SelectProductTileData(listenerSelectData).execute(urlSelectData)


        }
//        if (productData is FoodAndBev) {
//            var inflater: LayoutInflater = LayoutInflater.from(this)
//            var wizardView = inflater!!.inflate(R.layout.beverage_layout, mainCustomLayout, false)
//            mainCustomLayout?.addView(wizardView)
//
//            var imageView: ImageView = findViewById(R.id.food_and_bev_image)
//            var amount: Int = 1
//            Picasso.get().load(productData.foodAndBevImage).into(imageView)
////            wizardView.product_image.setImageResource(productData.imageResource)
////            wizardView.food_and_bev_image.setImageResource(R.drawable.coke_no_sugar)
//            wizardView.food_and_bev_brand.text = productData.foodAndBevBrand
//            wizardView.food_and_bev_vol.text = "(" + productData.foodAndBevSize + ")"
//            wizardView.food_and_bev_price.text = "฿" + NumberFormat.getInstance().format(productData.foodAndBevPrice!! * amount).toString()
//            wizardView.food_and_bev_cal.text = productData.foodAndBevCal
//            wizardView.food_and_bev_sugar.text = productData.foodAndBevSugar
//            wizardView.food_and_bev_fat.text = productData.foodAndBevFat
//            wizardView.food_and_bev_sodium.text = productData.foodAndBevSodium
//
//
//            wizardView.food_and_bev_beverage_increase.setOnClickListener {
//                amount++
//                wizardView.amount.text = amount.toString()
//                wizardView.food_and_bev_price.text = "฿" + NumberFormat.getInstance().format(productData.foodAndBevPrice!! * amount).toString()
//            }
//            wizardView.food_and_bev_beverage_decrease.setOnClickListener {
//                if (amount > 1) amount--
//                wizardView.amount.text = amount.toString()
//                wizardView.food_and_bev_price.text = "฿" + NumberFormat.getInstance().format(productData.foodAndBevPrice!! * amount).toString()
//            }
//            wizardView.food_and_bev_close.setOnClickListener {
//                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//            }
//
//            wizardView.food_and_bev_add_to_cart.setOnClickListener {
//                var order = Order(null, pref!!.getString("UUID", ""), null, null,
//                        null, null, arrayOf(OrderDetail(null, null, productData.foodAndBevId,
//                        null, amount)).toList())
//
//                val listenerPostOrder = object : InsertProductOrder.getDataComplete {
//                    override fun getDataComplete(jsonString: String) {
//                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//                    }
//                }
//
//                InsertProductOrder(listenerPostOrder, order).execute(IPAddress.ipAddress + "product-order/insertProductOrder/")
//            }
//            val urlSelectData = IPAddress.ipAddress + "product-data/selectOtherProductFoodAndBev/${productData.foodAndBevId}"
//            val listenerSelectData = object : SelectProductFoodData.getDataComplete {
//                override fun getDataComplete(foodAndBevList: List<FoodAndBev>) {
//
//                    var fbl = ArrayList<FoodAndBev>()
//                    fbl.add(foodAndBevList[0])
//                    fbl.add(foodAndBevList[1])
//                    fbl.add(foodAndBevList[0])
//                    fbl.add(foodAndBevList[1])
//                    fbl.add(foodAndBevList[0])
//                    fbl.add(foodAndBevList[1])
//
//                    wizardView.otherProductRecycleView.apply {
//                        layoutManager = LinearLayoutManager(this@LiveObjectDetectionActivity, LinearLayoutManager.HORIZONTAL, false)
////                        adapter = OtherProductFoodAndBevAdapter(foodAndBevList)
//                        adapter = OtherProductFoodAndBevAdapter(fbl.toList(), this@LiveObjectDetectionActivity)
//                    }
//
//                }
//
//            }
//
//            SelectProductFoodData(listenerSelectData).execute(urlSelectData)
//
//
//        }
//        else if (productData is Furniture) {
//            var wizardView = layoutInflater.inflate(R.layout.furniture_layout, mainCustomLayout, false)
//            mainCustomLayout?.addView(wizardView)
//
//            var imageView: ImageView = findViewById(R.id.furniture_image)
//            Picasso.get().load(productData.furnitureImage).into(imageView)
////            wizardView.furniture_product_image.setImageResource(productData.imageResource)
//            wizardView.furniture_image.setImageResource(R.drawable.gaming_chair)
//            wizardView.furniture_brand.text = productData.furnitureBrand
//            wizardView.furniture_model.text = productData.furnitureModel
//            wizardView.furniture_spec.text = productData.furnitureDetail
//            wizardView.furniture_price.text = "฿" + NumberFormat.getInstance().format(productData.furniturePrice).toString()
//            var amount: Int = 1
//            wizardView.furniture_increase.setOnClickListener {
//                amount++
//                wizardView.furniture_amount.text = amount.toString()
//            }
//            wizardView.furniture_decrease.setOnClickListener {
//                if (amount > 1) amount--
//                wizardView.furniture_amount.text = amount.toString()
//            }
//            wizardView.furniture_close.setOnClickListener {
//                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//            }
//            wizardView.furniture_add_to_cart.setOnClickListener {
//
//                var order = Order(null, pref!!.getString("UUID", ""), null, null,
//                        null, null, arrayOf(OrderDetail(null, null, productData.furnitureId,
//                        null, amount)).toList())
//
//                val listenerPostOrder = object : InsertProductOrder.getDataComplete {
//                    override fun getDataComplete(jsonString: String) {
//                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//                    }
//                }
//
//                InsertProductOrder(listenerPostOrder, order).execute(IPAddress.ipAddress + "product-order/insertProductOrder/")
//            }
//
//        } else if (productData is Electronic) {
//            var wizardView = layoutInflater.inflate(R.layout.electronic_layout, mainCustomLayout, false)
//            mainCustomLayout?.addView(wizardView)
//
//            var imageView: ImageView = findViewById(R.id.electronic_image)
//            Picasso.get().load(productData.electronicImage).into(imageView)
////            wizardView.furniture_product_image.setImageResource(productData.imageResource)
//            wizardView.electronic_brand.text = productData.electronicBrand
//            wizardView.electronic_model.text = productData.electronicModel
//            wizardView.electronic_spec.text = productData.electronicSpec
//            wizardView.electronic_price.text = "฿" + NumberFormat.getInstance().format(productData.electronicPrice).toString()
//            var amount: Int = 1
//            wizardView.electronic_increase.setOnClickListener {
//                amount++
//                wizardView.electronic_amount.text = amount.toString()
//            }
//            wizardView.electronic_decrease.setOnClickListener {
//                if (amount > 1) amount--
//                wizardView.electronic_amount.text = amount.toString()
//            }
//            wizardView.electronic_close.setOnClickListener {
//                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//            }
//            wizardView.electronic_add_to_cart.setOnClickListener {
//
//                var order = Order(null, pref!!.getString("UUID", ""), null, null,
//                        null, null, arrayOf(OrderDetail(null, null, productData.electronicId,
//                        null, amount)).toList())
//
//                val listenerPostOrder = object : InsertProductOrder.getDataComplete {
//                    override fun getDataComplete(jsonString: String) {
//                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
//                    }
//                }
//
//                InsertProductOrder(listenerPostOrder, order).execute(IPAddress.ipAddress + "product-order/insertProductOrder/")
//
//            }
//
//        }

    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowModel.WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        searchButton?.visibility = View.GONE
        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(
                        if (workflowState == WorkflowModel.WorkflowState.CONFIRMING)
                            R.string.prompt_hold_camera_steady
                        else
                            R.string.prompt_point_at_an_object)
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                searchProgressBar?.visibility = View.VISIBLE
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                stopCameraPreview()
            }
            else -> promptChip?.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && promptChipAnimator?.isRunning == false) {
            promptChipAnimator?.start()
        }
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.workflowModel ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e("LogBoom", "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (workflowModel?.isCameraLive == true) {
            workflowModel!!.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }


    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.product_search_button -> {
                searchButton?.isEnabled = false
                workflowModel?.onSearchButtonClicked()
            }
//            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
//            R.id.close_button -> onBackPressed()
            R.id.flash_button -> {
                if (flashButton?.isSelected == true) {
                    flashButton?.isSelected = false
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                } else {
                    flashButton?.isSelected = true
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                }
            }
//            R.id.settings_button -> {
//                settingsButton?.isEnabled = false
//                startActivity(Intent(this, SettingsActivity::class.java))
//            }
//            R.id.cartBtn -> {
////                if (Cart.cartItemList.size > 0)
//                startActivity(Intent(this, CartActivity::class.java))
////                else
////                    Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show()
//            }

//            R.id.historyScanBtn -> {
//                startActivity(Intent(this, HistoryScanActivity::class.java))
//            }
//
//            R.id.historyOrderBtn -> {
//                startActivity(Intent(this, HistoryOrderActivity::class.java))
//            }
//            R.id.logoutBtn -> {
////                var mGoogleSignInClient : GoogleSignInClient?=null
////                mGoogleSignInClient?.signOut()
//                val intent = Intent(this, LoginActivity::class.java)
////            Log.d("Address",oldHolder!!.address.text.toString())
//                intent.putExtra("Logout", "Logout")
//                startActivity(intent)
////                startActivity(Intent(this, LoginActivity::class.java))
//            }

        }
    }
}