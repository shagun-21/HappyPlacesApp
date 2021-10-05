package com.example.happyplaces.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.happyplaces.R
import com.example.happyplaces.database.DatabaseHandler
import com.example.happyplaces.databinding.ActivityHappyPlacesAddBinding
import com.example.happyplaces.model.HappyPlaceModel
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_happy_places_add.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class HappyPlacesAddActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityHappyPlacesAddBinding
    private var cal = Calendar.getInstance()
    private lateinit var dateSetListener: DatePickerDialog.OnDateSetListener
    private var tmpUri: Uri? = null
    private var imagePath: Uri? = null
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private var mHappyPlaceDetails: HappyPlaceModel? = null
    private lateinit var mFusedLocation: FusedLocationProviderClient

    private val selectImageFromGalleryResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { binding.ivPlaceImage.setImageURI(uri) }
            imagePath = saveImageToInternalStorage(binding.ivPlaceImage.drawable.toBitmap())
            Log.e("Saved image: ", "Path :: $imagePath")
        }

    private val takePhotoFromCameraResult =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                tmpUri?.let { uri -> binding.ivPlaceImage.setImageURI(uri) }
                imagePath = saveImageToInternalStorage(binding.ivPlaceImage.drawable.toBitmap())
                Log.e("Saved image: ", "Path :: $imagePath")
            }
        }

   private val placeAutocompleteResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val place = Autocomplete.getPlaceFromIntent(result.data!!)

                binding.etLocation.setText(place.address)
                mLatitude = place.latLng?.latitude!!
                mLongitude = place.latLng?.longitude!!
            }
        }

   private val locationPermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap[Manifest.permission.ACCESS_FINE_LOCATION]!! &&
                resultMap[Manifest.permission.ACCESS_COARSE_LOCATION]!!
            ) {
                requestNewLocationData()
            } else {
                showRationalDialogForPermission()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHappyPlacesAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarAddHappyPlaceActivity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbarAddHappyPlaceActivity.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            onBackPressed()
        }

        mFusedLocation = LocationServices.getFusedLocationProviderClient(this)

        if (!Places.isInitialized()) {
            Places.initialize(
                applicationContext,
                resources.getString(R.string.google_maps_api_key)
            )
        }

        if (intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlaceDetails =
                intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel?
        }

        dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->

            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }

        if (mHappyPlaceDetails != null) {
            supportActionBar?.title = "Edit Happy Place"

            binding.etTitle.setText(mHappyPlaceDetails!!.title)
            binding.etDescription.setText(mHappyPlaceDetails!!.description)
            binding.etDate.setText(mHappyPlaceDetails!!.date)
            binding.etLocation.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude

            imagePath = Uri.parse(mHappyPlaceDetails!!.image)
            binding.ivPlaceImage.setImageURI(imagePath)

            binding.btnSave.text = "UPDATE"
        }

        binding.etDate.setOnClickListener(this)
        binding.tvAddImage.setOnClickListener(this)
        binding.btnSave.setOnClickListener(this)
        binding.etLocation.setOnClickListener(this)
        binding.tvSelectCurrentLocation.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.et_date -> {
                DatePickerDialog(
                    this@HappyPlacesAddActivity,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                    .show()
            }
            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)
                pictureDialog.setTitle("Select Action")

                val pictureDialogItems: Array<String> =
                    arrayOf("Select photo from Gallery", "Capture photo from camera")

                pictureDialog.setItems(pictureDialogItems) { _, which ->
                    when (which) {
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }

                pictureDialog.show()
            }
           R.id.et_location -> {
                try {

                    val fields = listOf(
                        Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS
                    )
                    val intent = Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.FULLSCREEN,
                        fields
                    ).build(this@HappyPlacesAddActivity)
                    intent.putExtra("test", "podaci")

                    placeAutocompleteResult.launch(intent)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            R.id.btn_save -> {
                when {
                    binding.etTitle.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter the title", Toast.LENGTH_SHORT).show()
                    }
                    binding.etDescription.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter the description", Toast.LENGTH_SHORT)
                            .show()
                    }
                    binding.etLocation.text.isNullOrEmpty() -> {
                        Toast.makeText(this, "Please enter the location", Toast.LENGTH_SHORT).show()
                    }
                    imagePath == null -> {
                        Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val dbh = DatabaseHandler(this)
                        val id = if (mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id

                        val hpm = HappyPlaceModel(
                            id,
                            binding.etTitle.text.toString(),
                            imagePath.toString(),
                            binding.etDescription.text.toString(),
                            binding.etDate.text.toString(),
                            binding.etLocation.text.toString(),
                            mLatitude,
                            mLongitude
                        )

                        val result = if (mHappyPlaceDetails == null) {
                            dbh.addHappyPlace(hpm)
                        } else {
                            dbh.updateHappyPlace(hpm)
                        }

                        if (result > 0) {
                            setResult(Activity.RESULT_OK)
                            finish()
                        } else {
                            Log.e("AddHappyPlaceActivity: ", "Something went wrong")
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    }
                }
            }
           R.id.tv_select_current_location -> {
                if (!isLocationEnabled()) {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } else {
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    locationPermissionResult.launch(permissions)
                }
            }
        }
    }


    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            mLongitude = mLastLocation.longitude

            GlobalScope.launch(Dispatchers.Main) {
                val geocoder = Geocoder(this@HappyPlacesAddActivity, Locale.getDefault())

                try {
                    var addressList: List<Address>? =
                        geocoder.getFromLocation(mLatitude, mLongitude, 1)

                    if (addressList != null && addressList.isNotEmpty()) {
                        val address: Address = addressList[0]
                        val sb = StringBuilder()

                        for (i in 0..address.maxAddressLineIndex) {
                            sb.append(address.getAddressLine(i)).append(" ")
                        }

                        binding.etLocation.setText(sb.trim().toString())
                    }
                } catch (e: Exception) {
                    binding.etLocation.setText("Unknow location")
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest.create().apply {
            interval = 0
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }

        mFusedLocation.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun takePhotoFromCamera() {
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if (report!!.areAllPermissionsGranted()) {
                    getTmpFileUri().let { uri ->
                        tmpUri = uri
                        takePhotoFromCameraResult.launch(tmpUri)
                    }
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<PermissionRequest>,
                permissionToken: PermissionToken
            ) {
                showRationalDialogForPermission()
            }
        }).onSameThread().check()
    }

    private fun getTmpFileUri(): Uri {
        val ts = SimpleDateFormat.getDateTimeInstance().format(Date())
        val tmpFile = File.createTempFile("img_$ts", ".png", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }

        return FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.provider",
            tmpFile
        )
    }

    private fun choosePhotoFromGallery() {
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if (report!!.areAllPermissionsGranted()) {
                    selectImageFromGalleryResult.launch("image/*")
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<PermissionRequest>,
                permissionToken: PermissionToken
            ) {
                showRationalDialogForPermission()
            }
        }).onSameThread().check()
    }

    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage(
                "It looks like you have turned off permission required for this feature. " +
                        "It can be enabled under the Application Settings."
            ).setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun updateDateInView() {
        val myFormat = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        binding.etDate.setText(sdf.format(cal.time))
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {
        val wrapper = ContextWrapper(applicationContext)
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)
        file = File(file, "${UUID.randomUUID()}.jpg")

        try {
            val stream: OutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return Uri.parse(file.absolutePath)
    }

    companion object {
        private const val IMAGE_DIRECTORY = "HappyPlacesImages"
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE =3
    }
}
