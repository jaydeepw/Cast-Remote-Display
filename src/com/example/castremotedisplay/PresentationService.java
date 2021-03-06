/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.castremotedisplay;

import android.content.Context;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.gms.cast.CastPresentation;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Service to keep the remote display running even when the app goes into the background
 */
public class PresentationService extends CastRemoteDisplayLocalService {

    private static final String TAG = "PresentationService";

    // First screen
    private CastPresentation mPresentation;
    private MediaPlayer mMediaPlayer;
    private CubeRenderer mCubeRenderer;

    @Override
    public void onCreate() {
        super.onCreate();
        // Audio
        mMediaPlayer = MediaPlayer.create(this, R.raw.sound);
        mMediaPlayer.setVolume((float) 0.1, (float) 0.1);
        mMediaPlayer.setLooping(true);
    }

    @Override
    public void onCreatePresentation(Display display) {
        Log.i(TAG, "syatam: onCreatePresentation");
        createPresentation(display);
    }

    @Override
    public void onDismissPresentation() {
        dismissPresentation();
    }

    private void dismissPresentation() {
        if (mPresentation != null) {
            mMediaPlayer.stop();
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    private void createPresentation(Display display) {
        dismissPresentation();
        // mPresentation = new FirstScreenPresentation(this, display);
        mPresentation = new SecondScreenPresentation(this, display);

        try {
            mPresentation.show();
            mMediaPlayer.start();
        } catch (WindowManager.InvalidDisplayException ex) {
            Log.e(TAG, "Unable to show presentation, display was removed.", ex);
            dismissPresentation();
        }
    }

    /**
     * Utility method to allow the user to change the cube color.
     */
    public void changeColor() {
        mCubeRenderer.changeColor();
    }

    /**
     * The presentation to show on the first screen (the TV).
     * <p>
     * Note that this display may have different metrics from the display on
     * which the main activity is showing so we must be careful to use the
     * presentation's own {@link Context} whenever we load resources.
     * </p>
     */
    private class FirstScreenPresentation extends CastPresentation {

        private final String TAG = "FirstScreenPresentation";

        public FirstScreenPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.first_screen_layout);

            TextView titleTextView = (TextView) findViewById(R.id.title);
            // Use TrueType font to get best looking text on remote display
            Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf");
            titleTextView.setTypeface(typeface);

            GLSurfaceView firstScreenSurfaceView = (GLSurfaceView) findViewById(R.id.surface_view);
            // Create an OpenGL ES 2.0 context.
            firstScreenSurfaceView.setEGLContextClientVersion(2);
            // Allow UI elements above this surface; used for text overlay
            firstScreenSurfaceView.setZOrderMediaOverlay(true);
            // Enable anti-aliasing
            firstScreenSurfaceView.setEGLConfigChooser(new CustomConfigChooser());
            mCubeRenderer = new com.example.castremotedisplay.CubeRenderer();
            firstScreenSurfaceView.setRenderer(mCubeRenderer);
        }

        /**
         * OpenGL config to enable custom anti-aliasing
         */
        private final class CustomConfigChooser implements GLSurfaceView.EGLConfigChooser {

            private int[] mValue = new int[1];
            protected int mRedSize = 8;
            protected int mGreenSize = 8;
            protected int mBlueSize = 8;
            protected int mAlphaSize = 8;
            protected int mDepthSize = 16;
            protected int mStencilSize = 0;

            @Override
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                int[] configSpec = {
                        EGL10.EGL_RED_SIZE, mRedSize,
                        EGL10.EGL_GREEN_SIZE, mGreenSize,
                        EGL10.EGL_BLUE_SIZE, mBlueSize,
                        EGL10.EGL_ALPHA_SIZE, mAlphaSize,
                        EGL10.EGL_DEPTH_SIZE, mDepthSize,
                        EGL10.EGL_STENCIL_SIZE, mStencilSize,
                        EGL10.EGL_RENDERABLE_TYPE, 4,
                        EGL10.EGL_SAMPLE_BUFFERS, 1,
                        EGL10.EGL_SAMPLES, 4,
                        EGL10.EGL_NONE
                };
                int[] num_config = new int[1];
                if (!egl.eglChooseConfig(display, configSpec, null, 0, num_config)) {
                    throw new IllegalArgumentException("eglChooseConfig1 failed");
                }

                int numConfigs = num_config[0];

                if (numConfigs <= 0) {
                    // Don't do anti-aliasing
                    configSpec = new int[]{
                            EGL10.EGL_RED_SIZE, mRedSize,
                            EGL10.EGL_GREEN_SIZE, mGreenSize,
                            EGL10.EGL_BLUE_SIZE, mBlueSize,
                            EGL10.EGL_ALPHA_SIZE, mAlphaSize,
                            EGL10.EGL_DEPTH_SIZE, mDepthSize,
                            EGL10.EGL_STENCIL_SIZE, mStencilSize,
                            EGL10.EGL_RENDERABLE_TYPE, 4,
                            EGL10.EGL_NONE
                    };

                    if (!egl.eglChooseConfig(display, configSpec, null, 0, num_config)) {
                        throw new IllegalArgumentException("eglChooseConfig2 failed");
                    }
                    numConfigs = num_config[0];

                    if (numConfigs <= 0) {
                        throw new IllegalArgumentException("No configs match configSpec");
                    }
                }

                EGLConfig[] configs = new EGLConfig[numConfigs];
                if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs, num_config)) {
                    throw new IllegalArgumentException("eglChooseConfig3 failed");
                }
                EGLConfig config = findConfig(egl, display, configs);
                if (config == null) {
                    throw new IllegalArgumentException("No config chosen");
                }
                return config;
            }

            private EGLConfig findConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
                for (EGLConfig config : configs) {
                    int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                    int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                    if ((d >= mDepthSize) && (s >= mStencilSize)) {
                        int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                        int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                        int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                        int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                        if ((r == mRedSize) && (g == mGreenSize) && (b == mBlueSize) && (a
                                == mAlphaSize)) {
                            return config;
                        }
                    }
                }
                return null;
            }

            private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config,
                                         int attribute,
                                         int defaultValue) {
                if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
                    return mValue[0];
                }
                return defaultValue;
            }
        }
    }

    private class SecondScreenPresentation extends CastPresentation {

        private final String TAG = "FirstScreenPresentation";

        public SecondScreenPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.video_player_layout);
            initializePlayer();
        }

        private void initializePlayer() {
            // Create a default TrackSelector
            BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveTrackSelection.Factory(bandwidthMeter);
            TrackSelector trackSelector =
                    new DefaultTrackSelector(videoTrackSelectionFactory);

            //Initialize the player
            SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(getContext(), trackSelector);

            //Initialize simpleExoPlayerView
            SimpleExoPlayerView simpleExoPlayerView = findViewById(R.id.embeddedVideoView);
            simpleExoPlayerView.setPlayer(player);

            // Produces DataSource instances through which media data is loaded.
            DataSource.Factory dataSourceFactory =
                    new DefaultDataSourceFactory(getContext(), "sss");

            // Produces Extractor instances for parsing the media data.
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

            // This is the MediaSource representing the media to be played.
            Uri videoUri = Uri.parse("https://res.cloudinary.com/demo/video/upload/v1524750367/Liverpool_vs_Roma_sum.mp4");
            MediaSource videoSource = new ExtractorMediaSource(videoUri,
                    dataSourceFactory, extractorsFactory, null, null);

            // Prepare the player with the source.
            player.prepare(videoSource);
            player.setPlayWhenReady(true);
        }
    }

}
