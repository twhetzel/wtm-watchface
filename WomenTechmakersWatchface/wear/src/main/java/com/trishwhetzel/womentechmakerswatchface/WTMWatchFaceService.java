package com.trishwhetzel.womentechmakerswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by whetzel on 2/21/16.
 */
public class WTMWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "WTMWatchFaceService";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine {

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private static final int MSG_UPDATE_TIME = 0;

        private static final float HOUR_STROKE_WIDTH = 9f;
        private static final float MINUTE_STROKE_WIDTH = 7f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;

        private float mSecondHandLength;
        private float mMinuteHandLength;
        private float mHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;


        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;

        private Paint rectPaint;
        private Paint rectBkgPaint;
        private Paint datePaint;

        private int batteryLevel;

        private Paint batteryPaint;

        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private Bitmap mAmbientBackgroundBitmap;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private Paint mTextPaint;
        private float mXOffset;
        private float mYOffset;
        private float mTextSpacingHeight;
        private int mScreenTextColor = Color.DKGRAY;

        private Rect mPeekCardBounds = new Rect();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "updating time");
                }
                invalidate();
                if (shouldTimerBeRunning()) {
                    long timeMs = System.currentTimeMillis();
                    long delayMs = INTERACTIVE_UPDATE_RATE_MS
                            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                    mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                }

            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            /* Initialize your watch face */
            setWatchFaceStyle(new WatchFaceStyle.Builder(WTMWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo_v2);
            mAmbientBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ambient_logo_2);

            Resources resources = WTMWatchFaceService.this.getResources();

            mTextPaint = new Paint();
            mTextPaint.setColor(mScreenTextColor);
            mTextPaint.setTypeface(BOLD_TYPEFACE);
            mTextPaint.setAntiAlias(true);

            /* Set defaults for colors */
            mWatchHandColor = Color.GRAY;
            mWatchHandHighlightColor = Color.BLUE;
            mWatchHandShadowColor = Color.LTGRAY;

            /* Set parameters to draw Hour hand */
            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw Minute hand */
            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw Second hand */
            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw hour marks around watch face */
            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Set parameters to draw rectangle */
            rectPaint = new Paint();
            rectPaint.setColor(Color.LTGRAY);
            rectPaint.setStrokeWidth(3);
            rectPaint.setStyle(Paint.Style.STROKE);
            //rectPaint.setShadowLayer(SHADOW_RADIUS, 2, 2, Color.GRAY);

            /* Set parameters for background for rectangle */
            rectBkgPaint = new Paint ();
            rectBkgPaint.setColor(Color.WHITE);

            /* Set parameters to draw date */
            datePaint = new Paint();
            datePaint.setColor(mWatchHandHighlightColor);
            datePaint.setTextSize(resources.getDimension(R.dimen.date_size));
            datePaint.setAntiAlias(true);
            datePaint.setStyle(Paint.Style.FILL);
            datePaint.setStrokeWidth(2);

            /* Set parameters to draw battery level */
            batteryPaint = new Paint();
            batteryPaint.setColor(mWatchHandHighlightColor);
            batteryPaint.setTextSize(resources.getDimensionPixelSize(R.dimen.battery_level));
            batteryPaint.setAntiAlias(true);
            batteryPaint.setStyle(Paint.Style.FILL);
            batteryPaint.setStrokeWidth(2);

            /* Extract colors from background image to improve watch face style. */
            Palette.generateAsync(
                    mBackgroundBitmap,
                    new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            if (palette != null) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Palette: " + palette);
                                }

                                mWatchHandHighlightColor = palette.getVibrantColor(Color.BLUE);
                                mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                                mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                                updateWatchHandStyle();
                            }
                        }
                    });

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            /* the time changed */
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            /* the wearable switched between modes */
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.GRAY);
                mMinutePaint.setColor(Color.GRAY);
                mSecondPaint.setColor(Color.GRAY);
                mTickAndCirclePaint.setColor(Color.GRAY);
                rectBkgPaint.setColor(Color.TRANSPARENT);
                datePaint.setColor(Color.WHITE);
                batteryPaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                datePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                datePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);
                rectBkgPaint.setColor(Color.WHITE);
                datePaint.setColor(mWatchHandHighlightColor);
                batteryPaint.setColor(mWatchHandHighlightColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                datePaint.setAntiAlias(true);
                batteryPaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onSurfaceChanged");
            }
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.825);
            mMinuteHandLength = (float) (mCenterX * 0.75);
            mHourHandLength = (float) (mCenterX * 0.52);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scaleWidth = ((float) width) / (float) mBackgroundBitmap.getWidth();
            float scaleHeight = ((float) height) / (float) mBackgroundBitmap.getHeight();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scaleWidth),
                    (int) (mBackgroundBitmap.getHeight() * scaleHeight), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                //initGrayBackgroundBitmap();
                mAmbientBackgroundBitmap = Bitmap.createScaledBitmap(mAmbientBackgroundBitmap,
                        (int) (mAmbientBackgroundBitmap.getWidth() * scaleWidth),
                        (int) (mAmbientBackgroundBitmap.getHeight() * scaleHeight), true);
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
              Log.v(TAG, "onDraw");
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            /* draw your watch face */
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                //canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
                canvas.drawBitmap(mAmbientBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /* Display Date */
            String formattedDayOfMonth = new SimpleDateFormat("EEE dd").format(mCalendar.getTime());

            String dateText = String.valueOf(formattedDayOfMonth);
            float dateXOffset = computeXOffset(dateText, datePaint, bounds);
            //float dateYOffset = computeDateYOffset(dateText, datePaint);
             /* display rectangle to hold date value */
            //canvas.drawRect(30, 30, 60, 60, rectPaint);
//            canvas.drawRect(dateXOffset - 3f, mCenterY + 4f, dateXOffset + 28f, mCenterY - 23f, rectBkgPaint);
//            canvas.drawRect(dateXOffset - 3f, mCenterY + 4f, dateXOffset + 28f, mCenterY - 23f, rectPaint);
//            canvas.drawText(dateText, dateXOffset, mCenterY, datePaint);

            /* display rectangle to hold day and date */
            canvas.drawRect(dateXOffset - 21f, mCenterY + 4f, dateXOffset + 28f, mCenterY - 23f, rectBkgPaint);
            canvas.drawText(dateText, dateXOffset - 21f, mCenterY, datePaint);

            /* Get battery status level */
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = WTMWatchFaceService.this.registerReceiver(null, ifilter);
            batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

            /* Display Battery Level */
            String batteryLevelPercentage = String.valueOf(batteryLevel)+"%";
            float batteryXOffset = computeBatteryXOffset(batteryLevelPercentage, batteryPaint, bounds);
            canvas.drawText(batteryLevelPercentage, batteryXOffset, mCenterY, batteryPaint);


            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mMinuteHandLength,
                    mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);
            }
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        private float computeXOffset(String text, Paint paint, Rect watchBounds) {
            float centerX = watchBounds.exactCenterX();
            float timeLength = paint.measureText(text);
            //return centerX - (timeLength / 2.0f);
            return centerX + (mCenterX * 0.75f);
        }

        private float computeBatteryXOffset(String text, Paint paint, Rect watchBounds) {
            float centerX = watchBounds.exactCenterX();
            float battLength = paint.measureText(text);
            return centerX - (mCenterX * 0.9f);
        }

//        private float computeTimeYOffset(String timeText, Paint timePaint, Rect watchBounds) {
//            float centerY = watchBounds.exactCenterY();
//            Rect textBounds = new Rect();
//            timePaint.getTextBounds(timeText, 0, timeText.length(), textBounds);
//            int textHeight = textBounds.height();
//            return centerY + (textHeight / 2.0f);
//        }

//        private float computeDateYOffset(String dateText, Paint datePaint) {
//            Rect textBounds = new Rect();
//            datePaint.getTextBounds(dateText, 0, dateText.length(), textBounds);
//            //return textBounds.height() + 10.0f;
//            return textBounds.height() + 5.0f;
//        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */
            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));

            super.onApplyWindowInsets(insets);

            /** Loads offsets / text size based on device type (square vs. round). */
            Resources resources = WTMWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_x_offset_round : R.dimen.interactive_x_offset);
            mYOffset = resources.getDimension(
                    isRound ? R.dimen.interactive_y_offset_round : R.dimen.interactive_y_offset);

            float textSize = resources.getDimension(
                    isRound ? R.dimen.interactive_text_size_round : R.dimen.interactive_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            Log.d(TAG, "Registering receiver");

//            if (mRegisteredTimeZoneReceiver) {
//                return;
//            }
//            mRegisteredTimeZoneReceiver = true;
//            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
//            WTMWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);

            if (!mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                WTMWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
            }

            // Register battery level
//            if (!mRegisteredBatteryPercentageReceiver) {
//                mRegisteredBatteryPercentageReceiver = true;
//                IntentFilter filterBattery = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
//                WTMWatchFaceService.this.registerReceiver(mBatInfoReceiver, filterBattery);
//            }
        }

        private void unregisterReceiver() {
            Log.d(TAG, "Unregistering receiver");
//            if (!mRegisteredTimeZoneReceiver) {
//                return;
//            }
//            mRegisteredTimeZoneReceiver = false;

            if (mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                WTMWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            }

            // Unregister battery receiver
//            if (mRegisteredBatteryPercentageReceiver) {
//                mRegisteredBatteryPercentageReceiver = false;
//                WTMWatchFaceService.this.unregisterReceiver(mBatInfoReceiver);
//            }
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }
    }

}
