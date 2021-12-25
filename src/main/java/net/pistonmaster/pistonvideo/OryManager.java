package net.pistonmaster.pistonvideo;

import com.google.gson.Gson;
import net.pistonmaster.pistonvideo.templates.kratos.IdentityResponse;
import net.pistonmaster.pistonvideo.templates.kratos.WhoisResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Optional;

public class OryManager {
    public static final String ORY_PUBLIC = "http://localhost:4433/";
    public static final String ORY_PRIVATE = "http://localhost:4434/";

    public static Optional<WhoisResponse> getWhoisFromToken(String token) {
        if (token == null)
            return Optional.empty();

        try {
            OkHttpClient client = new OkHttpClient().newBuilder().addInterceptor(chain -> {
                final okhttp3.Request original = chain.request();
                final okhttp3.Request authorized = original.newBuilder()
                        .addHeader("Cookie", "ory_kratos_session=" + token)
                        .build();
                return chain.proceed(authorized);
            }).build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(ORY_PUBLIC + "sessions/whoami")
                    .build(); // defaults to GET

            okhttp3.Response response = client.newCall(request).execute();

            ResponseBody body = response.body();

            if (body == null || response.code() == 401) {
                return Optional.empty();
            } else {
                WhoisResponse whoisResponse = new Gson().fromJson(body.string(), WhoisResponse.class);

                return Optional.of(whoisResponse);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static Optional<IdentityResponse> getIdentity(String userId) {
        if (userId == null)
            return Optional.empty();

        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new okhttp3.Request.Builder()
                    .url(ORY_PRIVATE + "identities/" + userId)
                    .build(); // defaults to GET

            Response response = client.newCall(request).execute();

            ResponseBody body = response.body();

            if (body == null) {
                return Optional.empty();
            } else {
                String bodyString = body.string();
                IdentityResponse identityResponse = new Gson().fromJson(bodyString, IdentityResponse.class);

                if (identityResponse.getError() != null) {
                    return Optional.empty();
                }

                return Optional.of(identityResponse);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
