package com.broadway.customclock;

import java.util.Calendar;

import com.broadway.customclock.R;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.format.Time;
import android.widget.RemoteViews;

public class ClockProvider extends AppWidgetProvider {

	public static final String CLOCK_UPDATE = "com.broadway.customclock.CLOCK_UPDATE";
	public static final String SWITCH_COLORS_ACTION = "com.broadway.customclock.SWITCH_COLORS";
	private static final double SECONDS_IN_DAY = 86400.0;
	private static final double TICK = (SECONDS_IN_DAY / 65536.0) * 1000.0;
	private static final int CANVAS_SIZE = 384;
	private static final int DOT_SIZE = 12;

	// Colors
	private static final int WHITE = 0;
	private static final int WHITE_NO_BG = 1;
	private static final int GRAY = 2;
	private static final int BLUE = 3;
	private static final int RED = 4;
	private static final float STROKE_WIDTH = 12;
	private static int currentColor = 0;
	private static int onColor = Color.argb(0, 0, 255, 255);
	private static int offColor = Color.argb(255, 0, 255, 0);

	// Time Control
	private static int currentTime = 0;
	private static boolean isFirstCall = true;

	@Override
	public void onReceive(Context context, Intent intent) {

		super.onReceive(context, intent);

		// Get the widget manager and ids for this widget provider, then call the shared clock update method.
		ComponentName thisAppWidget = new ComponentName(context.getPackageName(), getClass().getName());
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

		// Clock Update Event
		if (CLOCK_UPDATE.equals(intent.getAction())) {
			int ids[] = appWidgetManager.getAppWidgetIds(thisAppWidget);
			for (int appWidgetID : ids) {
				updateClock(context, appWidgetManager, appWidgetID);
			}
		}

		// Touch Event
		if (SWITCH_COLORS_ACTION.equals(intent.getAction())) {
			changeColor();
		}
	}

	private PendingIntent createClockTickIntent(Context context) {
		Intent intent = new Intent(CLOCK_UPDATE);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		return pendingIntent;
	}

	private PendingIntent createColorSwitchIntent(Context context) {
		Intent intent = new Intent(SWITCH_COLORS_ACTION);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		return pendingIntent;
	}

	@Override
	public void onDisabled(Context context) {

		super.onDisabled(context);

		// Stop the Timer
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(createClockTickIntent(context));
	}

	@Override
	public void onEnabled(Context context) {

		super.onEnabled(context);

		// Create the Timer
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		alarmManager.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), (long) (TICK), createClockTickIntent(context));
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

		// Perform this loop procedure for each App Widget that belongs to this provider
		for (int i = 0; i < appWidgetIds.length; i++) {

			int appWidgetId = appWidgetIds[i];

			// Get the layout for the App Widget and attach an on-click listener to the button
			AppWidgetProviderInfo appInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
			if (context == null || appInfo == null) {
				return;
			}
			RemoteViews views = new RemoteViews(context.getPackageName(), appInfo.initialLayout);

			// Update The clock label using a shared method
			updateClock(context, appWidgetManager, appWidgetId);

			// Touch Intent
			PendingIntent p = createColorSwitchIntent(context);
			views.setOnClickPendingIntent(R.id.image, p);

			// Tell the AppWidgetManager to perform an update on the current app widget
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}

	public static void updateClock(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

		// Get a reference to our Remote View
		AppWidgetProviderInfo appInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
		RemoteViews views = new RemoteViews(context.getPackageName(), appInfo.initialLayout);
		
		// Update clock
		if (isFirstCall) { resyncTime(); isFirstCall = false; } 
		else {
			// To avoid precision errors
			if (currentTime % 64 == 0) resyncTime(); 
			else currentTime++; 
		}

		// Draw the dots
		Bitmap widgetBitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Config.ARGB_8888);
		Canvas widgetCanvas = new Canvas(widgetBitmap);
		Paint widgetPaint = new Paint();
		widgetPaint.setAntiAlias(true);
		widgetPaint.setStyle(Paint.Style.STROKE);
		widgetPaint.setStrokeWidth(STROKE_WIDTH);
		
		widgetPaint.setColor(onColor);
		
		drawBackground(widgetCanvas, widgetPaint);
		drawHashMarks(widgetCanvas, widgetPaint);
		drawNumbers(widgetCanvas, widgetPaint);
		drawHands(widgetCanvas, widgetPaint);
		
//		for (int i = 0; i < 16; i++) {
//			
//			if (((currentTime >> i) & 0x01) == 1) { widgetPaintInstance.setColor(onColor); } 
//			else { widgetPaintInstance.setColor(offColor); }
//			
//			widgetCanvasInstance.drawCircle(CANVAS_SIZE/2, CANVAS_SIZE/2, 45, widgetPaintInstance);
//			
//			canvas.drawCircle(DOT_SIZE + ((DOT_SIZE * 10) * (i % 4)), DOT_SIZE + ((DOT_SIZE * 10) * ((i / 4) % 4)), DOT_SIZE, p);
//		}

		views.setImageViewBitmap(R.id.image, widgetBitmap);
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}

	private static void resyncTime() {
		Time today = new Time(Time.getCurrentTimezone());
		today.setToNow();
		int hour = today.hour;
		int minute = today.minute;
		int second = today.second;

		double currentSeconds = second + (minute * 60) + (hour * 60 * 60);
		currentTime = (int) (currentSeconds / (TICK / 1000.0));
	}

	private static void changeColor() {

		currentColor++;
		if (currentColor > 4) {
			currentColor = 0;
		}

		switch (currentColor) {
		case WHITE:
			onColor = Color.argb(255, 242, 242, 242);
			offColor = Color.argb(128, 64, 64, 64);
			break;
		case WHITE_NO_BG:
			onColor = Color.argb(255, 242, 242, 242);
			offColor = Color.argb(0, 0, 0, 0);
			break;
		case GRAY:
			onColor = Color.argb(255, 64, 64, 64);
			offColor = Color.argb(64, 64, 64, 64);
			break;
		case BLUE:
			onColor = Color.argb(255, 63, 156, 255);
			offColor = Color.argb(64, 63, 156, 255);
			break;
		case RED:
			onColor = Color.argb(225, 232, 46, 46);
			offColor = Color.argb(64, 242, 46, 46);
			break;
		default:
			break;
		}
	}

	private static void drawBackground(Canvas widgeCanv, Paint widgePaint){
		widgeCanv.drawCircle(CANVAS_SIZE/2, CANVAS_SIZE/2, (float) (CANVAS_SIZE*0.4), widgePaint);
	}
	
	private static void drawHashMarks(Canvas widgeCanv, Paint widgePaint){
		
	}
	
	private static void drawNumbers(Canvas widgeCanv, Paint widgePaint){
		
	}
	
	private static void drawHands(Canvas widgeCanv, Paint widgePaint){
		
	}
}
