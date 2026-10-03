package com.annetv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    private static final String PREFS = "anne_tv";
    private static final String KEY_LAST_ID = "last_channel_id";
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; SmartTV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private final StringBuilder digitBuffer = new StringBuilder();

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout banner;
    private TextView bannerNumber;
    private TextView bannerName;
    private TextView bannerStatus;
    private LinearLayout channelPanel;
    private LinearLayout channelRows;
    private TextView centerMessage;
    private UpdateChecker updateChecker;

    private int currentIndex = 0;
    private int currentStreamIndex = 0;
    private int reconnectAttempt = 0;
    private boolean panelVisible = false;

    private final Runnable hideBanner = () -> banner.setVisibility(View.GONE);
    private final Runnable commitDigits = this::switchFromDigits;
    private final Runnable reconnectRunnable = this::restartCurrent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        buildUi();
        buildPlayer();

        new ChannelRepository(this).load(this::applyChannels);
        updateChecker = new UpdateChecker(this);
        updateChecker.check();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        centerMessage = new TextView(this);
        centerMessage.setTextColor(Color.WHITE);
        centerMessage.setTextSize(22);
        centerMessage.setGravity(Gravity.CENTER);
        centerMessage.setVisibility(View.GONE);
        centerMessage.setBackgroundColor(0xB3000000);
        FrameLayout.LayoutParams cm = new FrameLayout.LayoutParams(dp(560), dp(92), Gravity.CENTER);
        root.addView(centerMessage, cm);

        buildBanner();
        buildChannelPanel();
    }

    private void buildBanner() {
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(22), dp(14), dp(28), dp(14));
        banner.setBackgroundColor(0xE6111111);
        banner.setVisibility(View.GONE);

        bannerNumber = new TextView(this);
        bannerNumber.setTextColor(Color.WHITE);
        bannerNumber.setTextSize(34);
        bannerNumber.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        banner.addView(bannerNumber);

        bannerName = new TextView(this);
        bannerName.setTextColor(Color.WHITE);
        bannerName.setTextSize(24);
        bannerName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        banner.addView(bannerName);

        bannerStatus = new TextView(this);
        bannerStatus.setTextColor(0xB3FFFFFF);
        bannerStatus.setTextSize(15);
        banner.addView(bannerStatus);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(360), -2, Gravity.START | Gravity.BOTTOM);
        p.setMargins(dp(36), 0, 0, dp(38));
        root.addView(banner, p);
    }

    private void buildChannelPanel() {
        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelPanel.setPadding(dp(20), dp(18), dp(20), dp(18));
        channelPanel.setBackgroundColor(0xF20A0A0A);
        channelPanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText(R.string.channels_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, dp(14));
        channelPanel.addView(title);

        ScrollView scroll = new ScrollView(this);
        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(channelRows, new ScrollView.LayoutParams(-1, -2));
        channelPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(440), -1, Gravity.START);
        root.addView(channelPanel, pp);
    }

    private void buildPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && player.getPlayWhenReady()) {
                    reconnectAttempt = 0;
                    hideCenterMessage();
                    if (!channels.isEmpty()) showBanner(channels.get(currentIndex), getString(R.string.live));
                } else if (state == Player.STATE_BUFFERING) {
                    if (!channels.isEmpty()) showBanner(channels.get(currentIndex), getString(R.string.loading));
                }
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                tryFallbackOrReconnect();
            }
        });
    }

    private void applyChannels(List<Channel> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            showCenterMessage(getString(R.string.no_channels));
            return;
        }

        Channel previous = channels.isEmpty() ? null : channels.get(currentIndex);
        String playingId = previous == null ? null : previous.id;
        channels.clear();
        channels.addAll(incoming);
        rebuildRows();

        String desired = playingId;
        if (desired == null) {
            SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            desired = p.getString(KEY_LAST_ID, "");
        }
        int found = indexById(desired);
        currentIndex = found >= 0 ? found : firstPlayableIndex();

        boolean sourceChanged = previous != null && found >= 0
                && (!previous.streamUrls.equals(channels.get(found).streamUrls)
                || !previous.referer.equals(channels.get(found).referer)
                || !previous.origin.equals(channels.get(found).origin)
                || !previous.userAgent.equals(channels.get(found).userAgent));
        if (player.getCurrentMediaItem() == null || found < 0 || sourceChanged) playCurrent();
    }

    private int firstPlayableIndex() {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).hasStreams()) return i;
        }
        return 0;
    }

    private void rebuildRows() {
        channelRows.removeAllViews();
        for (int i = 0; i < channels.size(); i++) {
            final int index = i;
            Channel c = channels.get(i);
            TextView row = new TextView(this);
            row.setText(String.format(Locale.getDefault(), "%02d   %s%s", c.number, c.favorite ? "★ " : "", c.name));
            row.setTextSize(21);
            row.setTextColor(Color.WHITE);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setFocusable(true);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackgroundColor(0x00111111);
            row.setOnFocusChangeListener((v, hasFocus) -> {
                TextView t = (TextView) v;
                if (hasFocus) {
                    t.setBackgroundColor(Color.WHITE);
                    t.setTextColor(Color.BLACK);
                } else {
                    t.setBackgroundColor(Color.TRANSPARENT);
                    t.setTextColor(Color.WHITE);
                }
            });
            row.setOnClickListener(v -> {
                currentIndex = index;
                hidePanel();
                playCurrent();
            });
            channelRows.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }
    }

    private void playCurrent() {
        if (channels.isEmpty()) return;
        Channel c = channels.get(currentIndex);
        remember(c);
        handler.removeCallbacks(reconnectRunnable);
        reconnectAttempt = 0;
        currentStreamIndex = 0;
        showBanner(c, getString(R.string.loading));

        if (!c.hasStreams()) {
            player.stop();
            showCenterMessage(c.name + "\n\n" + getString(R.string.stream_unavailable));
            return;
        }

        playCurrentStream(false);
    }

    private void playCurrentStream(boolean reconnecting) {
        if (channels.isEmpty()) return;
        Channel c = channels.get(currentIndex);
        if (!c.hasStreams()) return;
        String url = c.streamAt(currentStreamIndex);
        if (TextUtils.isEmpty(url)) return;

        if (reconnecting) {
            showCenterMessage(getString(R.string.reconnecting));
        } else {
            hideCenterMessage();
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8");
        if (!TextUtils.isEmpty(c.referer)) headers.put("Referer", c.referer);
        if (!TextUtils.isEmpty(c.origin)) headers.put("Origin", c.origin);

        String userAgent = TextUtils.isEmpty(c.userAgent) ? DEFAULT_USER_AGENT : c.userAgent;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(12000)
                .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        HlsMediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url));

        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
    }

    private void tryFallbackOrReconnect() {
        if (channels.isEmpty()) return;
        Channel c = channels.get(currentIndex);
        if (currentStreamIndex + 1 < c.streamUrls.size()) {
            currentStreamIndex++;
            handler.removeCallbacks(reconnectRunnable);
            showCenterMessage(getString(R.string.trying_backup));
            showBanner(c, getString(R.string.backup_stream));
            playCurrentStream(false);
            return;
        }
        scheduleReconnect();
    }

    private void restartCurrent() {
        if (channels.isEmpty()) return;
        Channel c = channels.get(currentIndex);
        if (!c.hasStreams()) return;
        currentStreamIndex = 0;
        playCurrentStream(true);
    }

    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable);
        long[] delays = {1000, 2000, 4000, 8000, 15000};
        long delay = delays[Math.min(reconnectAttempt, delays.length - 1)];
        reconnectAttempt++;
        showCenterMessage(getString(R.string.reconnecting));
        handler.postDelayed(reconnectRunnable, delay);
    }

    private void next(int delta) {
        if (channels.isEmpty()) return;
        currentIndex = (currentIndex + delta + channels.size()) % channels.size();
        playCurrent();
    }

    private void showBanner(Channel c, String status) {
        bannerNumber.setText(String.valueOf(c.number));
        bannerName.setText(c.name);
        bannerStatus.setText(status);
        banner.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideBanner);
        handler.postDelayed(hideBanner, 3000);
    }

    private void togglePanel() {
        if (panelVisible) hidePanel(); else showPanel();
    }

    private void showPanel() {
        panelVisible = true;
        channelPanel.setVisibility(View.VISIBLE);
        if (channelRows.getChildCount() > 0) {
            View target = channelRows.getChildAt(Math.min(currentIndex, channelRows.getChildCount() - 1));
            target.requestFocus();
        }
    }

    private void hidePanel() {
        panelVisible = false;
        channelPanel.setVisibility(View.GONE);
        root.requestFocus();
    }

    private void appendDigit(int digit) {
        digitBuffer.append(digit);
        handler.removeCallbacks(commitDigits);
        handler.postDelayed(commitDigits, 1200);
        showCenterMessage(digitBuffer.toString());
    }

    private void switchFromDigits() {
        try {
            int n = Integer.parseInt(digitBuffer.toString());
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i).number == n) {
                    currentIndex = i;
                    hideCenterMessage();
                    playCurrent();
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            digitBuffer.setLength(0);
            hideCenterMessage();
        }
    }

    private void showCenterMessage(String text) {
        centerMessage.setText(text);
        centerMessage.setVisibility(View.VISIBLE);
    }

    private void hideCenterMessage() {
        if (digitBuffer.length() == 0) centerMessage.setVisibility(View.GONE);
    }

    private void remember(Channel c) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_ID, c.id).apply();
    }

    private int indexById(String id) {
        if (TextUtils.isEmpty(id)) return -1;
        for (int i = 0; i < channels.size(); i++) if (id.equals(channels.get(i).id)) return i;
        return -1;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int k = event.getKeyCode();

        if (panelVisible) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) {
                hidePanel(); return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            next(1); return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT) {
            next(-1); return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_MENU) {
            togglePanel(); return true;
        }
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) {
            appendDigit(k - KeyEvent.KEYCODE_0); return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (updateChecker != null) updateChecker.onHostResume();
        if (player != null && player.getCurrentMediaItem() != null) player.play();
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) player.release();
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
