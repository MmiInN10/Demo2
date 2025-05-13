package com.live2d.demo.full;
/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */
// import 추가
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Environment;
import androidx.fragment.app.FragmentActivity;
import android.speech.tts.TextToSpeech;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.content.Intent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
import com.google.android.material.navigation.NavigationBarView;
import com.live2d.demo.R;
import com.live2d.demo.databinding.ActivityMainBinding;
import com.live2d.demo.full.GLRenderer;
import com.live2d.demo.full.LAppDelegate;
import java.util.Locale;
import android.widget.Button;
import android.widget.ImageView;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.io.IOException;
import android.view.ScaleGestureDetector;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import java.util.Collections;
import android.graphics.Color;
import com.live2d.demo.schedule.AddActivity;
import com.live2d.demo.schedule.CalendarFragment;
import com.live2d.demo.schedule.HomeFragment;
import com.live2d.demo.schedule.ScheduleActivity;
import com.live2d.demo.schedule.SettingFragment;

public class MainActivity extends FragmentActivity {
    private GLSurfaceView glSurfaceView;
    private ImageView accessoryView;
    private Button scheduleButton;
    private FrameLayout rootLayout;

    private GLRenderer glRenderer;

    // 🎙️ 추가되는 부분
    private MediaRecorder mediaRecorder;
    private TextToSpeech textToSpeech;
    private String audioFilePath;
    private GoogleAccountCredential credential;
    private ActivityMainBinding binding;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final String OPENAI_API_KEY = "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";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    // 악세서리가 화면 밖으로 못 나가게 제한하는 함수
    private void constrainAccessoryInsideScreen(View v) {
        float x = v.getX();
        float y = v.getY();
        float width = v.getWidth() * v.getScaleX();
        float height = v.getHeight() * v.getScaleY();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // 왼쪽, 위쪽 경계 체크
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        // 오른쪽, 아래쪽 경계 체크
        if (x + width > screenWidth) x = screenWidth - width;
        if (y + height > screenHeight) y = screenHeight - height;

        v.setX(x);
        v.setY(y);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // activity_main.xml 사용

        // 1. 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.GET_ACCOUNTS}, 1);
        }

        // 2. Google Calendar 인증 설정
        credential = GoogleAccountCredential.usingOAuth2(
                        getApplicationContext(), Collections.singleton(CalendarScopes.CALENDAR))
                .setBackOff(new ExponentialBackOff());

        // 3. 레이아웃 참조
        rootLayout = findViewById(R.id.root_layout);

        // 4. GLSurfaceView 설정
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        glRenderer = new GLRenderer();
        glSurfaceView.setRenderer(glRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // GLSurfaceView 터치 이벤트 처리
        glSurfaceView.setOnTouchListener((v, event) -> {
            final float pointX = event.getX();
            final float pointY = event.getY();
            glSurfaceView.queueEvent(() -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        LAppDelegate.getInstance().onTouchBegan(pointX, pointY);
                        break;
                    case MotionEvent.ACTION_UP:
                        LAppDelegate.getInstance().onTouchEnd(pointX, pointY);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        LAppDelegate.getInstance().onTouchMoved(pointX, pointY);
                        break;
                }
            });
            return true;
        });

        // 5. 악세서리 이미지 뷰
        accessoryView = new ImageView(this);
        accessoryView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams accessoryParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        accessoryParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        accessoryParams.topMargin = 200;
        accessoryView.setLayoutParams(accessoryParams);

        // 6. 악세서리 선택 버튼
        Button accessoryMenuButton = new Button(this);
        accessoryMenuButton.setText("악세서리 선택");
        FrameLayout.LayoutParams menuButtonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        menuButtonParams.gravity = Gravity.BOTTOM | Gravity.END;
        menuButtonParams.bottomMargin = 150;
        menuButtonParams.rightMargin = 50;
        accessoryMenuButton.setLayoutParams(menuButtonParams);

        accessoryMenuButton.setOnClickListener(v -> {
            String[] accessories = {"모자", "안경", "리본"};
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("악세서리 선택")
                    .setItems(accessories, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                loadAccessoryFromAssets(accessoryView, "hat.png");
                                break;
                            case 1:
                                loadAccessoryFromAssets(accessoryView, "glasses.png");
                                break;
                            case 2:
                                loadAccessoryFromAssets(accessoryView, "ribbon.png");
                                break;
                        }
                        accessoryView.setVisibility(View.VISIBLE);
                    });
            builder.show();
        });

        // 7. 악세서리 제거 버튼
        Button removeAccessoryButton = new Button(this);
        removeAccessoryButton.setText("악세서리 제거");
        FrameLayout.LayoutParams removeButtonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        removeButtonParams.gravity = Gravity.BOTTOM | Gravity.END;
        removeButtonParams.bottomMargin = 50;
        removeButtonParams.rightMargin = 50;
        removeAccessoryButton.setLayoutParams(removeButtonParams);
        removeAccessoryButton.setOnClickListener(v -> accessoryView.setVisibility(View.INVISIBLE));

        // 8. 일정 관리 버튼
        Button scheduleButton = new Button(this);
        scheduleButton.setText("일정 관리");
        scheduleButton.setBackgroundColor(Color.parseColor("#6200EE"));
        scheduleButton.setTextColor(Color.WHITE);
        scheduleButton.setTextSize(16);
        scheduleButton.setPadding(20, 10, 20, 10);
        scheduleButton.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams scheduleButtonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        scheduleButtonParams.gravity = Gravity.TOP | Gravity.END;
        scheduleButtonParams.topMargin = 100;
        scheduleButtonParams.rightMargin = 50;
        scheduleButton.setLayoutParams(scheduleButtonParams);
        scheduleButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            startActivity(intent);
        });

        // 9. 말하기 버튼
        Button recordButton = new Button(this);
        recordButton.setText("말하기");
        FrameLayout.LayoutParams recordButtonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        recordButtonParams.gravity = Gravity.TOP | Gravity.START;
        recordButtonParams.topMargin = 100;
        recordButtonParams.leftMargin = 100;
        recordButton.setLayoutParams(recordButtonParams);
        recordButton.setOnClickListener(v -> {
            if (mediaRecorder == null) {
                startRecording();
            } else {
                stopRecording();
                transcribeAudio();
            }
        });

        // 10. 뷰를 레이아웃에 추가 (순서 중요)
        rootLayout.addView(glSurfaceView);           // Live2D 모델
        rootLayout.addView(accessoryView);           // 악세서리 이미지
        rootLayout.addView(accessoryMenuButton);     // 악세서리 선택 버튼
        rootLayout.addView(removeAccessoryButton);   // 악세서리 제거 버튼
        rootLayout.addView(scheduleButton);          // 일정 관리 버튼
        rootLayout.addView(recordButton);            // 말하기 버튼

        // 11. TTS 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.KOREAN);
            }
        });

        // 12. Live2D 앱 초기화
        LAppDelegate.getInstance().onStart(this);
        // 뷰 바인딩 초기화 후 레이아웃 설정
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 하단 바 설정
        setBottomNavigationView();

        // 처음 실행 시 홈 화면 표시
        if (savedInstanceState == null) {
            binding.bottomNavigationView.setSelectedItemId(R.id.fragment_home);
        }
    }

    private void loadAccessoryFromAssets(ImageView view, String fileName) {
        try {
            InputStream inputStream = getAssets().open(fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            view.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void startRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.release();  // 이미 있으면 먼저 해제
            mediaRecorder = null;
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(audioFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void transcribeAudio() {
        executorService.execute(() -> {
            try {
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) return;

                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", audioFile.getName(),
                                RequestBody.create(audioFile, MediaType.parse("audio/mp4")))
                        .addFormDataPart("model", "whisper-1")
                        .addFormDataPart("language", "ko")
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.openai.com/v1/audio/transcriptions")
                        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String result = new JSONObject(response.body().string()).getString("text");
                    sendMessageToGPT(result);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMessageToGPT(String userInput) {
        executorService.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "너는 한국어 AI 친구야. 사용자 질문에 너무 길지 않게 답변해줘."));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", userInput));

                JSONObject json = new JSONObject();
                json.put("model", "gpt-3.5-turbo");
                json.put("messages", messages);

                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                        .url(OPENAI_API_URL)
                        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String botReply = new JSONObject(response.body().string())
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    analyzeEmotion(botReply);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void analyzeEmotion(String botReply) {
        executorService.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "다음 문장을 읽고 '기쁨', '슬픔', '화남', '평온' 중 하나로만 답해주세요."));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", botReply));

                JSONObject json = new JSONObject();
                json.put("model", "gpt-3.5-turbo");
                json.put("messages", messages);

                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                        .url(OPENAI_API_URL)
                        .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String emotion = new JSONObject(response.body().string())
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    runOnUiThread(() -> updateCharacterEmotion(emotion));
                    runOnUiThread(() -> {
                        if (textToSpeech != null) {
                            textToSpeech.speak(botReply, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateCharacterEmotion(String emotion) {
        try {
            if (LAppDelegate.getInstance() != null
                    && LAppDelegate.getInstance().getLive2DManager() != null
                    && LAppDelegate.getInstance().getLive2DManager().getModel(0) != null) {

                if (emotion.contains("기쁨")) {
                    LAppDelegate.getInstance().getLive2DManager().getModel(0).setExpression("smile");
                } else if (emotion.contains("슬픔")) {
                    LAppDelegate.getInstance().getLive2DManager().getModel(0).setExpression("sad");
                } else if (emotion.contains("화남")) {
                    LAppDelegate.getInstance().getLive2DManager().getModel(0).setExpression("angry");
                } else if (emotion.contains("평온")) {
                    LAppDelegate.getInstance().getLive2DManager().getModel(0).setExpression("neutral");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
    private void setBottomNavigationView() {
        binding.bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                if (item.getItemId() == R.id.fragment_home) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.main_container, new HomeFragment())
                            .commit();
                    return true;
                } else if (item.getItemId() == R.id.fragment_calendar) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.main_container, new CalendarFragment())
                            .commit();
                    return true;
                } else if (item.getItemId() == R.id.fragment_settings) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.main_container, new SettingFragment())
                            .commit();
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

}
