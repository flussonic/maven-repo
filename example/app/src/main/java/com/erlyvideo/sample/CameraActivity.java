package com.erlyvideo.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.erlyvideo.sample.Common.NavigationTab;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import flussonic.watcher.sdk.domain.pojo.Camera;
import flussonic.watcher.sdk.domain.pojo.Track;
import flussonic.watcher.sdk.domain.pojo.UpdateProgressEvent;
import flussonic.watcher.sdk.domain.utils.CalendarUtils;
import flussonic.watcher.sdk.presentation.core.listeners.FlussonicBufferingListener;
import flussonic.watcher.sdk.presentation.core.listeners.FlussonicCollapseExpandTimelineListener;
import flussonic.watcher.sdk.presentation.core.listeners.FlussonicDownloadRequestListener;
import flussonic.watcher.sdk.presentation.core.listeners.FlussonicUpdateProgressEventListener;
import flussonic.watcher.sdk.presentation.watcher.FlussonicWatcherView;
import timber.log.Timber;


public class CameraActivity extends AppCompatActivity implements FlussonicCollapseExpandTimelineListener, FlussonicDownloadRequestListener, FlussonicBufferingListener, FlussonicUpdateProgressEventListener, FlussonicWatcherView.FlussonicExoPlayerErrorListener {

    public static final String SERVER = "https://cloud.vsaas.io";

    private static final String EXTRA_CAMERA = "EXTRA_CAMERA";
    private static final String EXTRA_SESSION = "EXTRA_SESSION";
    private static final String EXTRA_CAMERAS = "EXTRA_CAMERAS";
    private static final String EXTRA_START_POSITION = "EXTRA_START_POSITION";

    private NavigationTab navigationTab;

    private FlussonicWatcherView flussonicWatcherView;
    private long startPosition;
    private Camera camera;
    private String session;
    private List<Camera> cameras;
    private String login;
    private String password;

    public static Intent getStartIntent(@NonNull Context context,
                                        @NonNull Camera camera,
                                        @NonNull String session,
                                        @NonNull List<Camera> cameras,
                                        long startPosition,
                                        String login,
                                        String password) {
        Intent intent = new Intent(context, CameraActivity.class);
        intent.putExtra(EXTRA_CAMERA, camera);
        intent.putExtra(EXTRA_SESSION, session);
        intent.putExtra(EXTRA_CAMERAS, new ArrayList<>(cameras));
        intent.putExtra(EXTRA_START_POSITION, startPosition);
        intent.putExtra("Login", login);
        intent.putExtra("Password", password);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        camera = savedInstanceState == null
                ? getIntent().getParcelableExtra(EXTRA_CAMERA)
                : savedInstanceState.getParcelable(EXTRA_CAMERA);
        session = getIntent().getStringExtra(EXTRA_SESSION);
        cameras = getIntent().getParcelableArrayListExtra(EXTRA_CAMERAS);
        startPosition = getIntent().getLongExtra(EXTRA_START_POSITION, 0);

        login = getIntent().getStringExtra("Login");
        password = getIntent().getStringExtra("Password");

        navigationTab = new NavigationTab(findViewById(R.id.navigation));
        navigationTab.initCam(this, login, password);
        if (camera != null) {
            navigationTab.setTitle(camera.name());
            setupWatcher();
        } else {
            finish();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }

    private void setupWatcher() {
        // Чтобы плеер не пересоздавался (и в частности чтобы пауза плеера сохранялась)
        // при повороте экрана, добавьте в манифест активити, использующей
        // FlussonicWatcherView, атрибут android:configChanges="orientation|screenSize"

        // Инициализация компонента Watcher
        // Watcher представляет собой player + timeline
        flussonicWatcherView = findViewById(R.id.watcher_view);

        // Параметр allowDownload можно задать как в xml-разметке, так и в коде

        // allow download -- разрешить загрузку части архива
//        flussonicWatcherView.setAllowDownload(Settings.allowDownload(this));

        flussonicWatcherView.setStartPosition(startPosition);

//        flussonicWatcherView.setToolbarHeight(toolbarHeight);
        flussonicWatcherView.disableAudio(true);
        // Установка слушателя, чтобы запускать анимацию тулбара синхронно анимации таймлайна
        flussonicWatcherView.setCollapseExpandTimelineListener(this);

        // Установка слушателя на события буферизации
        flussonicWatcherView.setBufferingListener(this);

        // Установка слушателя запроса на сохранения части архива
        flussonicWatcherView.setDownloadRequestListener(this);

        // Установка слушателя, который вызывается раз в секунду, в параметре передается текущее
        // время проигрывания, UTC, в секундах
        flussonicWatcherView.setUpdateProgressEventListener(this);

        flussonicWatcherView.setExoPlayerErrorListener(this);

        // Инициализация параметров подключения к камере
        flussonicWatcherView.initialize(this);
        setUrl(false);
        flussonicWatcherView.enableTimelineMarkersV2(true);
    }

    private void setUrl(boolean setStartPositionFromUrl) {
        try {
            if (camera != null) {
                URL serverUrl = new URL(SERVER);
                String urlString = String.format(Locale.US, "%s://%s@%s/%s%s",
                        serverUrl.getProtocol(),
                        session,
                        serverUrl.getHost(),
                        camera.name(),
                        setStartPositionFromUrl ? "?from=" + startPosition : "");
                flussonicWatcherView.setUrl(urlString);
            } else {
                finish();
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("failed to set url", e);
        }
    }


    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Timber.w("onLowMemory");
        // При нехватке памяти можно попытаться очистить кэш с загруженными зонами
        flussonicWatcherView.clearCache();
    }

    @Override
    public void collapseToolbar(int i) {
        Timber.d("collapseToolbar");
    }

    @Override
    public void expandToolbar(int i) {
        Timber.d("expandToolbar");
    }

    @Override
    public void showToolbar(int i) {
        Timber.d("showToolbar");
    }

    @Override
    public void hideToolbar(int i) {
        Timber.d("hideToolbar");
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        overridePendingTransition(0, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
        Intent intent = new Intent(this, CamsActivity.class);
        intent.putExtra("Login", login);
        intent.putExtra("Password", password);
        this.startActivity(intent);

    }

    @Override
    public void onDownloadRequest(long from, long to) {
        String fromString = CalendarUtils.toString(from, CalendarUtils.DATE_TIME_PATTERN);
        String toString = CalendarUtils.toString(to, CalendarUtils.DATE_TIME_PATTERN);
        String msg = String.format("onDownloadRequest: from %s to %s", fromString, toString);
        Timber.d(msg);
        showToast(msg);
    }

    @Override
    public void onBufferingStart() {
        Timber.d("onBufferingStart");
    }

    @Override
    public void onBufferingStop() {
        Timber.d("onBufferingStop");
    }

    @Override
    public void onUpdateProgress(@NonNull UpdateProgressEvent event) {
        // event.currentUtcInSeconds() is the same as flussonicWatcherView.getCurrentUtcInSeconds()
        // event.playbackStatus() is the same as flussonicWatcherView.getPlaybackStatus()
        // event.speed() is the same as flussonicWatcherView.getSpeed()
        List<Track> tracks = flussonicWatcherView.getAvailableTracks();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            sb.append(tracks.get(i).trackId());
            if (i < tracks.size() - 1) {
                sb.append(", ");
            }
        }
        String tracksString = sb.toString();
        Track currentTrack = flussonicWatcherView.getCurrentTrack();
        Timber.d("update progress: %s %s %s %s %s",
                String.valueOf(event.currentUtcInSeconds()),
                String.valueOf(event.playbackStatus()), 
                String.format(Locale.US, "%.1f", event.speed()),
                 tracksString.isEmpty() ? "NO" : tracksString, 
                 currentTrack == null ? "NO" : currentTrack.trackId());
                /*textViewUtc.setText(String.valueOf(event.currentUtcInSeconds()));
                textViewStatus.setText(String.valueOf(event.playbackStatus()));
                textViewSpeed.setText(String.format(Locale.US, "%.1f", event.speed()));
                textViewTracks.setText(tracksString.isEmpty() ? "NO" : tracksString);
                textViewCurrentTrack.setText(currentTrack == null ? "NO" : currentTrack.trackId());*/
    }

    @Override
    public void onExoPlayerError(String code, String message, String url) {
        Timber.e("onExoPlayerError code: %s, message: %s, player_url: %s", code, message, url);
    }
}