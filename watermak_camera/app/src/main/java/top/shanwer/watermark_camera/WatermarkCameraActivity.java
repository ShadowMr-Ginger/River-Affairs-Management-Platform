package top.shanwer.watermark_camera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.app.ProgressDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import android.location.LocationManager;
import android.location.LocationListener;
import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WatermarkCameraActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private boolean isLocationReady = false;
    private Location lastKnownLocation;
    private static final String[] REQUIRED_PERMISSIONS;


    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13及以上
            REQUIRED_PERMISSIONS = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else { // Android 12及以下
            REQUIRED_PERMISSIONS = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    private ImageCapture imageCapture;
    private PreviewView viewFinder;
    private LocationManager locationManager;
    private String currentLocation = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark_camera);

        viewFinder = findViewById(R.id.viewFinder);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (allPermissionsGranted()) {
            startCamera();
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        findViewById(R.id.capture_button).setOnClickListener(v -> takePhoto());
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Log.e("CameraX", "启动相机失败", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000, // 最小时间间隔，毫秒
                    10,    // 最小距离间隔，米
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            updateLocationString(location);
                        }

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {
                            Toast.makeText(WatermarkCameraActivity.this, 
                                "请开启GPS定位", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}
                    },
                    Looper.getMainLooper()
                );
            } else {
                Toast.makeText(this, "请开启GPS定位", Toast.LENGTH_SHORT).show();
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isLocationReady || TextUtils.isEmpty(currentLocation)) {
                    Toast.makeText(this, "定位超时，使用最后已知位置", Toast.LENGTH_LONG).show();
                    if (lastKnownLocation != null) {
                        updateLocationString(lastKnownLocation);
                    } else {
                        // 设置默认江阴坐标
                        Location defaultLoc = new Location("江苏省江阴市");
                        defaultLoc.setLatitude(31.9086);
                        defaultLoc.setLongitude(120.2653);
                        updateLocationString(defaultLoc);
                    }
                }
            }, 15000);
        }
    }

    private void updateLocationString(Location location) {
        if (location == null) return;
        lastKnownLocation = location;

        currentLocation = String.format("位置: %.6f, %.6f", location.getLatitude(), location.getLongitude());
        isLocationReady = true;
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        if (!isLocationReady || lastKnownLocation == null) {
            Toast.makeText(this, "正在获取定位信息，请稍候再拍摄...", Toast.LENGTH_SHORT).show();
            return;
        }
        // 添加拍照动画
        View shutterButton = findViewById(R.id.capture_button);
        shutterButton.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction(() -> shutterButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(100)
                .start())
            .start();

        // 闪白动画
        View whiteOverlay = new View(this);
        whiteOverlay.setBackgroundColor(Color.WHITE);
        ((ViewGroup) viewFinder.getParent()).addView(whiteOverlay, 
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        whiteOverlay.animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction(() -> 
                ((ViewGroup) viewFinder.getParent()).removeView(whiteOverlay))
            .start();

        File photoFile = new File(getExternalFilesDir(null), 
            "Photo_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = 
            new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, 
            ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(ImageCapture.OutputFileResults output) {
                    try {
                        // 生成带水印的图片
                        Bitmap watermarkedBitmap = addWatermark(photoFile);
                        // 保存到相册
                        saveToGallery(watermarkedBitmap, photoFile);
                        // 保存为临时文件用于上传
                        File watermarkedFile = saveBitmapToFile(watermarkedBitmap);
                        // 显示上传对话框
                        showUploadDialog(watermarkedFile);
                    } catch (IOException e) {
                        Log.e("CameraX", "处理图片失败", e);
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException exc) {
                    Log.e("CameraX", "拍照失败", exc);
                }
            });
    }

    private Bitmap addWatermark(File photoFile) throws IOException {
        Bitmap original = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        Bitmap mutable = original.copy(Bitmap.Config.ARGB_8888, true);
        original.recycle();

        Canvas canvas = new Canvas(mutable);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(mutable.getWidth() / 30f); // 字体大小基于图片宽度
        paint.setShadowLayer(5f, 3f, 3f, Color.BLACK);

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date());
        String watermarkText = String.format("时间: %s\n%s", timeStamp, currentLocation);

        // 动态计算位置（关键修改部分）
        float marginRatio = 0.02f; // 边距比例（2%）
        float x = mutable.getWidth() * marginRatio;
        float bottomMarginRatio = 0.15f; // 距离底部的比例（15%）
        float startY = mutable.getHeight() * (1 - bottomMarginRatio);

        // 计算多行文本高度
        String[] lines = watermarkText.split("\n");
        float lineHeight = paint.getFontSpacing(); // 推荐行高
        float totalTextHeight = lineHeight * lines.length;

        // 确保水印不超出顶部（安全检查）
        if (startY - totalTextHeight < 0) {
            startY = totalTextHeight + mutable.getHeight() * marginRatio;
        }

        // 绘制每一行
        float currentY = startY;
        for (String line : lines) {
            canvas.drawText(line, x, currentY, paint);
            currentY += lineHeight;
        }

        return mutable;
    }

    private void saveToGallery(Bitmap bitmap, File photoFile) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, 
            "Watermarked_" + System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        
        Uri uri = getContentResolver().insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out);
            photoFile.delete();
        } catch (IOException e) {
            Log.e("CameraX", "保存到相册失败", e);
        }
    }

    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        File outputFile = new File(getExternalFilesDir(null), 
            "WatermarkedPhoto_" + System.currentTimeMillis() + ".jpg");
        
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out);
        }
        
        return outputFile;
    }

    private void showUploadDialog(File watermarkedFile) {
        ArrayList<String> categories = new ArrayList<>();
        ArrayList<Integer> categoryIds = new ArrayList<>();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_upload_info, null);
        Spinner riverSpinner = dialogView.findViewById(R.id.spinner_river);
        Spinner categorySpinner = dialogView.findViewById(R.id.spinner_category);
        EditText workStatusEdit = dialogView.findViewById(R.id.edit_work_status);

        AccessTokenUtil.sendAuthenticatedRequest(MainActivity.backendURL + "/pictureCategory/list?type=" + 0, new Callback() {

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        JSONArray data = jsonResponse.getJSONArray("data");
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject category = data.getJSONObject(i);
                            categories.add(category.getString("name"));
                            categoryIds.add(category.getInt("id"));
                        }
                        AccessTokenUtil.checkAndUpdateAccessToken(response, MainActivity.sharedPreferences);

                        ArrayAdapter<String> categorySpinnerAdapter = new ArrayAdapter<>(WatermarkCameraActivity.this, android.R.layout.simple_spinner_item, categories);
                        categorySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        categorySpinner.setAdapter(categorySpinnerAdapter);
                    } catch (JSONException | IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(WatermarkCameraActivity.this,
                            "获取分类失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }, MainActivity.sharedPreferences);



        // 设置河道列表
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.河道列表, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        riverSpinner.setAdapter(adapter);

        new AlertDialog.Builder(this)
            .setTitle("上传信息")
            .setView(dialogView)
            .setPositiveButton("确定", (dialog, which) -> {
                String selectedRiver = riverSpinner.getSelectedItem().toString();
                String workStatus = workStatusEdit.getText().toString();
                Integer selectedCategoryIndex = categorySpinner.getSelectedItemPosition();
                uploadToServer(watermarkedFile, selectedRiver, workStatus, categoryIds.get(selectedCategoryIndex));
            })
            .setNegativeButton("取消", (dialog, which) -> watermarkedFile.delete())
            .show();
    }

    private void uploadToServer(File watermarkedFile, String river, String workStatus, Integer categoryId) {
        // 显示上传进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在上传...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", watermarkedFile.getName(),
                        RequestBody.create(MediaType.parse("image/jpeg"), watermarkedFile))
                .addFormDataPart("river", river)
                .addFormDataPart("workStatus", workStatus)
                .addFormDataPart("categoryId", String.valueOf(categoryId))
                .build();

        AccessTokenUtil.sendAuthenticatedRequest(MainActivity.backendURL + "/riverCleaning/upload", requestBody, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(WatermarkCameraActivity.this,
                            "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                watermarkedFile.delete();
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(WatermarkCameraActivity.this,
                            "上传成功", Toast.LENGTH_SHORT).show();
                    AccessTokenUtil.checkAndUpdateAccessToken(response, MainActivity.sharedPreferences);
                });
                // 清理临时文件
                watermarkedFile.delete();
            }
        }, MainActivity.sharedPreferences);


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                startLocationUpdates();
            } else {
                Toast.makeText(this, "需要相机、存储和位置权限才能使用此功能", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
