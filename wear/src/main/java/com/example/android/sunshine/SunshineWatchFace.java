package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author Abel Suviri
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final int MSG_UPDATE_TIME = 0;
    private static final int INTERACTIVE_UPDATE_RATE_MS = 500;
    private static final String TAG = "WATCHFACE";
    private static final String PATH = "/weather";
    private static final String ICON_KEY = "icon";
    private static final String MAX_TEMP_KEY = "title";
    private static final String MIN_TEMP_KEY = "content";
    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final long TIMEOUT_MS = 500;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mLowBitAmbient;
        boolean mAmbient;
        float mLineHeight;
        String mMaxTemperature;
        String mMinTemperature;
        Bitmap mIcon = null;

        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mColonPaint;
        Paint mDatePaint;
        Paint mIconPaint;
        Paint mMaxTemperaturePaint;
        Paint mMinTemperaturePaint;
        Paint mLinePaint;
        Resources mResources;
        Calendar mCalendar;
        Date mDate;
        java.text.SimpleDateFormat mDateFormat;

        GoogleApiClient mGoogleApiClient;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this).build());
            mResources = SunshineWatchFace.this.getResources();
            mLineHeight = mResources.getDimension(R.dimen.line_height);
            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

            initPaints();
            initFormats();
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(onConnectedResultCallback);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(TAG, "Connection Failed with result " + connectionResult.getErrorCode());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            disconnectGoogleApiClient();
            super.onDestroy();
        }

        private void disconnectGoogleApiClient() {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        private void initPaints() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ResourcesCompat.getColor(mResources, R.color.blue_bkg, null));
            mHourPaint = createTextPaint(BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(NORMAL_TYPEFACE);
            mColonPaint = createTextPaint(NORMAL_TYPEFACE);
            mDatePaint = createTextPaint(NORMAL_TYPEFACE);
            mMaxTemperaturePaint = createTextPaint(BOLD_TYPEFACE);
            mMinTemperaturePaint = createTextPaint(NORMAL_TYPEFACE);
            mLinePaint = new Paint();
            mLinePaint.setColor(ResourcesCompat.getColor(mResources, R.color.white, null));
        }

        private Paint createTextPaint(Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);

            return paint;
        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat("EEE dd MMM YYYY", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                disconnectGoogleApiClient();
                unregisterReceiver();
            }

            updateTimer();
            initFormats();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            boolean isRound = insets.isRound();
            float textSize = mResources.getDimension(isRound ? R.dimen.digital_text_size : R.dimen.digital_text_size);
            float dateTextSize = textSize / 3;
            float temperatureTextSize = textSize / 1.5f;

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mColonPaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setTextSize(dateTextSize);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mMaxTemperaturePaint.setTextSize(temperatureTextSize);
            mMaxTemperaturePaint.setTextAlign(Paint.Align.CENTER);
            mMinTemperaturePaint.setTextSize(temperatureTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;
                    mHourPaint.setAntiAlias(antiAlias);
                    mMinutePaint.setAntiAlias(antiAlias);
                    mColonPaint.setAntiAlias(antiAlias);
                    mDatePaint.setAntiAlias(antiAlias);
                    mMaxTemperaturePaint.setAntiAlias(antiAlias);
                    mMinTemperaturePaint.setAntiAlias(antiAlias);
                    mLinePaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }

            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            String hour = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            String minute = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.MINUTE));
            String colon = ":";
            String date = mDateFormat.format(mDate);

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mHourPaint.setStyle(Paint.Style.STROKE);
                mMinutePaint.setStyle(Paint.Style.STROKE);
                mColonPaint.setStyle(Paint.Style.STROKE);

                float center = centerY + mHourPaint.measureText(hour) / 4;
                canvas.drawText(colon, centerX, center, mColonPaint);
                canvas.drawText(hour, centerX - mHourPaint.measureText(hour) - mColonPaint.measureText(colon),
                    center, mHourPaint);
                canvas.drawText(minute, centerX + mColonPaint.measureText(colon), center, mMinutePaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                mHourPaint.setStyle(Paint.Style.FILL);
                mMinutePaint.setStyle(Paint.Style.FILL);
                mColonPaint.setStyle(Paint.Style.FILL);

                canvas.drawLine(centerX - mLineHeight, centerY, centerX + mLineHeight, centerY, mLinePaint);
                canvas.drawText(date, centerX, centerY - mLineHeight / 1.5f, mDatePaint);

                float topDate = centerY - mLineHeight * 2;
                canvas.drawText(colon, centerX, topDate, mColonPaint);
                canvas.drawText(hour, centerX - mHourPaint.measureText(hour) - mColonPaint.measureText(colon),
                    topDate, mHourPaint);
                canvas.drawText(minute, centerX + mColonPaint.measureText(colon), topDate, mMinutePaint);

                float belowLine = centerY + mLineHeight * 2;
                if (!TextUtils.isEmpty(mMaxTemperature)) {
                    canvas.drawText(mMaxTemperature, centerX, belowLine, mMaxTemperaturePaint);
                }

                if (!TextUtils.isEmpty(mMinTemperature)) {
                    canvas.drawText(mMinTemperature, centerX + mMaxTemperaturePaint.measureText(mMaxTemperature) / 2,
                        belowLine, mMinTemperaturePaint);
                }

                if (mIcon != null) {
                    canvas.drawBitmap(mIcon, centerX - mMaxTemperaturePaint.measureText(mMaxTemperature) * 1.5f,
                        belowLine - mMaxTemperaturePaint.measureText(mMaxTemperature) / 2, mIconPaint);
                }
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent event : dataEventBuffer) {
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo(PATH) == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    mMaxTemperature = dataMap.getString(MAX_TEMP_KEY);
                    mMinTemperature = dataMap.getString(MIN_TEMP_KEY);
                    getWeatherIcon(dataMap.getAsset(ICON_KEY));
                }
            }
        }

        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    if (item.getUri().getPath().compareTo(PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        mMaxTemperature = dataMap.getString(MAX_TEMP_KEY);
                        mMinTemperature = dataMap.getString(MIN_TEMP_KEY);
                        getWeatherIcon(dataMap.getAsset(ICON_KEY));
                    }
                }

                dataItems.release();
                invalidate();
            }
        };

        private void getWeatherIcon(final Asset asset) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap icon = loadBitmapFromAsset(asset);

                    if (icon != null) {
                        mIcon = getResizedBitmap(icon);
                    }
                }
            }).start();
        }

        private Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                return null;
            }

            ConnectionResult result = mGoogleApiClient.blockingConnect(TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

            if (!result.isSuccess()) {
                return null;
            }

            InputStream inputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset)
                .await().getInputStream();


            if (inputStream == null) {
                return null;
            }

            return BitmapFactory.decodeStream(inputStream);
        }

        private Bitmap getResizedBitmap(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            float scaleWidth =  (width / 2f) / width;
            float scaleHeight = (height / 2f) / height;

            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleHeight);

            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
            bitmap.recycle();

            return resizedBitmap;
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
