package top.shanwer.watermark_camera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import com.auth0.android.jwt.JWT;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    public static final String backendURL = "https://a.api.jyhedaoqingjie.cn:8081";
    static SharedPreferences sharedPreferences;
    private String accessToken;
    private EditText editTextUsername;
    private EditText editTextPassword;
    private EditText editTextRegisterUsername;
    private EditText editTextRegisterPassword;
    private EditText editTextRegisterConfirmPassword;

    private Button buttonLogin;
    private Button buttonRegister;
    private Button buttonToggle;
    private ViewFlipper viewFlipper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("CameraPrefs", MODE_PRIVATE);

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextRegisterUsername = findViewById(R.id.editTextRegisterUsername);
        editTextRegisterPassword = findViewById(R.id.editTextRegisterPassword);
        editTextRegisterConfirmPassword = findViewById(R.id.editTextRegisterConfirmPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);
        buttonToggle = findViewById(R.id.buttonToggle);
        viewFlipper = findViewById(R.id.viewFlipper);

        accessToken = AccessTokenUtil.getAccessToken(sharedPreferences);
        if (accessToken != null && !accessToken.isEmpty() && !isTokenExpired(accessToken)) {
            // 如果 token 未过期，跳转到水印相机界面
            Intent intent = new Intent(MainActivity.this, WatermarkCameraActivity.class);
            startActivity(intent);
            finish();
        }

        buttonLogin.setOnClickListener(v -> {
            String username = editTextUsername.getText().toString();
            String password = editTextPassword.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(MainActivity.this, "用户名和密码不能为空", Toast.LENGTH_SHORT).show();
            } else {
                login(username, password);
            }
        });

        buttonRegister.setOnClickListener(v -> {
            String username = editTextRegisterUsername.getText().toString();
            String password = editTextRegisterPassword.getText().toString();
            String confirmPassword = editTextRegisterConfirmPassword.getText().toString();
            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(MainActivity.this, "用户名、密码和确认密码不能为空", Toast.LENGTH_SHORT).show();
            } else if(password.length() < 8){
                Toast.makeText(MainActivity.this, "密码长度不能小于8位", Toast.LENGTH_SHORT).show();
            } else if (password.equals(confirmPassword)) {
                register(username, password);
            } else {
                // 显示密码不匹配的提示
                Toast.makeText(MainActivity.this, "密码与确认密码不匹配", Toast.LENGTH_SHORT).show();
            }
        });

        buttonToggle.setOnClickListener(v -> {
            viewFlipper.showNext();
            if (viewFlipper.getDisplayedChild() == 0) {
                buttonToggle.setText(R.string.button_toggle_to_register);
            } else {
                buttonToggle.setText(R.string.button_toggle_to_login);
            }
        });
    }


    private void login(String username, String password) {
        OkHttpClient client = new OkHttpClient();

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("username", username);
            jsonObject.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), JSON);
        Request request = new Request.Builder()
                .url(backendURL + "/auth/login")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "登录失败，请检查网络连接: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        JSONObject data = jsonResponse.getJSONObject("data");
                        accessToken = data.getString("accessToken");
                        AccessTokenUtil.saveAccessToken(accessToken, sharedPreferences);
                        Intent intent = new Intent(MainActivity.this, WatermarkCameraActivity.class);
                        startActivity(intent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else{
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "登录失败，请检查用户名或密码", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void register(String username, String password) {
        OkHttpClient client = new OkHttpClient();

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("username", username);
            jsonObject.put("password", password);
            jsonObject.put("identity", 1);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), JSON);
        Request request = new Request.Builder()
                .url(backendURL + "/auth/register")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        JSONObject data = jsonResponse.getJSONObject("data");
                        accessToken = data.getString("accessToken");
                        AccessTokenUtil.saveAccessToken(accessToken, sharedPreferences);
                        Intent intent = new Intent(MainActivity.this, WatermarkCameraActivity.class);
                        startActivity(intent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else{
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "注册失败，请检查用户名或密码", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }



    private boolean isTokenExpired(String token) {
        JWT jwt = new JWT(token);
        long exp = jwt.getClaim("exp").asLong();
        long currentTime = System.currentTimeMillis() / 1000;
        return currentTime >= exp;
    }
}