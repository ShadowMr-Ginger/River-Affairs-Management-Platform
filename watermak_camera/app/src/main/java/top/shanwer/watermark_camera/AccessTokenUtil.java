package top.shanwer.watermark_camera;

import android.content.SharedPreferences;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AccessTokenUtil {
    static String getAccessToken(SharedPreferences sharedPreferences) {
        return sharedPreferences.getString("accessToken", null);
    }

    static void saveAccessToken(String accessToken, SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("accessToken", accessToken);
        editor.apply();
    }

    static void sendAuthenticatedRequest(String url, RequestBody body, Callback callback, SharedPreferences sharedPreferences) {
        OkHttpClient client = new OkHttpClient();

        String accessToken = getAccessToken(sharedPreferences);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(callback);
    }

    static void sendAuthenticatedRequest(String url, Callback callback, SharedPreferences sharedPreferences) {
        OkHttpClient client = new OkHttpClient();

        String accessToken = getAccessToken(sharedPreferences);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(callback);
    }

    static void checkAndUpdateAccessToken(Response response, SharedPreferences sharedPreferences){
        if (response.isSuccessful()) {
            String tokenString = response.header("Authorization");
            if(tokenString == null){
                return;
            }
            String token = tokenString.substring(tokenString.indexOf(" ") + 1);
            saveAccessToken(token, sharedPreferences);
        }
    }
}
