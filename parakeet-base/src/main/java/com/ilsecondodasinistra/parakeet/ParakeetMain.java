package com.ilsecondodasinistra.parakeet;

import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.AlarmClock;
import android.support.annotation.MainThread;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.github.amlcurran.showcaseview.ShowcaseView;
import com.ilsecondodasinistra.parakeet.CustomAdapter.ThingToDoCallback;
import com.ilsecondodasinistra.parakeet.EnhancedListView.OnDismissCallback;
import com.ilsecondodasinistra.parakeet.EnhancedListView.Undoable;
import com.ilsecondodasinistra.parakeet.shared.AboutActivity;

public class ParakeetMain extends AppCompatActivity implements ThingToDoCallback, OnClickListener {

	/*
	 * Constants used for date formats
	 */
	final int FORMAT_TYPE_DATE = 0;
	final int FORMAT_TYPE_MINUTES = 1;
	final int FORMAT_TYPE_DATE_COMPLETE = 2;
	
	TextView timeToWakeUp;
	TextView firstLaunchHint;
	TextView timeToLeave;
	TextView timeToLeaveLabel;
	List<ThingToDo> listOfThingsToDo;
	EnhancedListView listOfToDo;
	
	CustomAdapter listCustomAdapter;
	
	ParakeetConfig config = new ParakeetConfig();
	
	ImageButton setAlarmButton;
	ImageButton newThingToDoButton;
	ImageButton pencilButton;
	
	private Dialog dialog;
	
    private DrawerLayoutHelper drawerLayoutHelper;
    ActionBar actionBar;
    
    SharedPreferences prefs;
	
	ShowcaseView showcaseView;
//	ShowcaseView.ConfigOptions mOptions = new ShowcaseView.ConfigOptions();
    public static final float SHOWCASE_SPINNER_SCALE = 1f;
    public static final float SHOWCASE_OVERFLOW_ITEM_SCALE = 0.5f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_parakeet_main);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		Intent launchingIntent = getIntent();
		if(launchingIntent.getAction() != null && launchingIntent.getAction().length() > 0)
            Toast.makeText(ParakeetMain.this, launchingIntent.getAction(), Toast.LENGTH_SHORT).show();

		/* Gets actionBar for ApplicationDrawer */
		actionBar = getSupportActionBar();
		actionBar.setTitle(getString(R.string.app_name));
		
		timeToWakeUp = (TextView) findViewById(R.id.timeToWakeUp);
		firstLaunchHint = (TextView) findViewById(R.id.firstLaunchHint);
		timeToLeave = (TextView) findViewById(R.id.timeToLeave);
		timeToLeaveLabel = (TextView) findViewById(R.id.time_to_leave_label);
		pencilButton = (ImageButton)findViewById(R.id.modify_timetoleave_button);

		Typeface tf = Typeface.createFromAsset(getAssets(),"fonts/digital-7.ttf");

		timeToWakeUp.setTypeface(tf);
		timeToLeave.setTypeface(tf);

		OnClickListener chooseTimeClickListener = new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				/*
				 * When clicking on "time to leave home" label the edit box
				 * gets opened
				 */
				chooseTime();
			}
		};
		
		timeToLeave.setOnClickListener(chooseTimeClickListener);
		timeToLeaveLabel.setOnClickListener(chooseTimeClickListener);
		pencilButton.setOnClickListener(chooseTimeClickListener);

		checkSavedTimes();
		
	    // Show the "What's New" screen once for each new release of the application
	    new WhatsNewScreen(this).show();
		
		/*
		 * Deletes all notifications
		 */
	    deleteAllNotifications();

		listOfToDo = (EnhancedListView) findViewById(R.id.todoList);
		if(listOfThingsToDo == null)
		{
			listOfThingsToDo = config.getNewListOfThingsToDo();
		}
		else
		{
			config.setListOfThingsToDo(listOfThingsToDo);
		}

		listCustomAdapter = new CustomAdapter(this, R.layout.row,
				listOfThingsToDo);
		listCustomAdapter.setCallback(this);
		listOfToDo.setAdapter(listCustomAdapter);

		listOfToDo.setDismissCallback(new OnDismissCallback() {
			
			@Override
			public Undoable onDismiss(EnhancedListView listView, final int position) {
				final ThingToDo thing = (ThingToDo) listCustomAdapter.getItem(position);
				listCustomAdapter.remove(thing);
				return new EnhancedListView.Undoable() {
					
					@Override
					public void undo() {
						// TODO Auto-generated method stub
						listCustomAdapter.insert(thing, position);
					}
				
			    // Return a string for your item
			    @Override public String getTitle() {
			      return getString(R.string.after_remove) + " " + thing.getName();
			    }		    
				};
			}
		});
		listOfToDo.enableSwipeToDismiss();
		
		calculateWakeUpTime();

		newThingToDoButton = (ImageButton)findViewById(R.id.new_thing);
				
		newThingToDoButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
		        dialog = new Dialog(ParakeetMain.this);
                dialog.setContentView(R.layout.row_add_layout);
	                dialog.findViewById(R.id.button_cancel).setOnClickListener(
	                		ParakeetMain.this);
	                dialog.findViewById(R.id.button_add).setOnClickListener(ParakeetMain.this);
	                dialog.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.add_activity_title);
	                dialog.setTitle(getText(R.string.add_activity_title));
                dialog.show();
			}
		});
		
		setAlarmButton = (ImageButton)findViewById(R.id.set_alarm);
		
		setAlarmButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				setAlarm();
			}
		});
		
		listOfToDo.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
//				Toast.makeText(parent.getContext(), "Riga "+position, 200).show();
		        dialog = new Dialog(ParakeetMain.this);
                dialog.setContentView(R.layout.row_modify_layout);
                dialog.findViewById(R.id.button_cancel).setOnClickListener(
                		ParakeetMain.this);
                dialog.findViewById(R.id.button_modify).setOnClickListener(ParakeetMain.this);
                dialog.findViewById(R.id.button_modify).setTag(position);
                dialog.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.add_activity_title);
                dialog.setTitle(getText(R.string.modify_activity_title));
                
                /*
                 * Sets fields with values of selected row
                 */
                TextView thingToDoName = (TextView)dialog.findViewById(R.id.name);
                thingToDoName.setText(listOfThingsToDo.get(position).getName());

                TextView thingToDoLength = (TextView)dialog.findViewById(R.id.length);
                thingToDoLength.setText(listOfThingsToDo.get(position).getStringLength());

                dialog.show();

			}
		});
		
//		showHelpIfNeeded(ShowcaseView.TYPE_ONE_SHOT);
	}

	TimePickerDialog.OnTimeSetListener timePickerListener = new TimePickerDialog.OnTimeSetListener() {
		@Override
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			config.setDateTimeTo(Calendar.HOUR_OF_DAY, hourOfDay);
			config.setDateTimeTo(Calendar.MINUTE, minute);
			
			/*
			 * Check if the alarm must be set for today or tomorrow
			 */
			Calendar cal = Calendar.getInstance();

			Log.i("parakeet", "hourOfDay: " + hourOfDay);
			Log.i("parakeet", "minute: " + minute);
			Log.i("parakeet", "Calendar.HOUR_OF_DAY: " + cal.get(Calendar.HOUR_OF_DAY));
			Log.i("parakeet", "Calendar.MINUTE: " + cal.get(Calendar.MINUTE));
			
			if(	hourOfDay < cal.get(Calendar.HOUR_OF_DAY)
				|| (hourOfDay == cal.get(Calendar.HOUR_OF_DAY)
					&& minute <= cal.get(Calendar.MINUTE)))
			{
				//Tomorrow
				config.setDateTimeTo(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH) + 1);
//				Toast.makeText(ParakeetMain.this, "L'allarme è per domani "+dateAndTimeFormatter.format(dateTime.getTime()), 2000).show();
			}
			else {
				config.setDateTimeTo(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH));
//				Toast.makeText(ParakeetMain.this, "L'allarme è per oggi "+dateAndTimeFormatter.format(dateTime.getTime()), 2000).show();
			}

			updateLabel();			//Updates timeToLeave label

			config.setDateTimeToLeave(config.getDateTime().getTime());
			
//			Toast.makeText(ParakeetMain.this, "Ora di uscire: " + config.getDateFormattedDate(config.getDateTimeToLeave(), FORMAT_TYPE_DATE_COMPLETE), 2000).show();
			
//			try {
//				/*
//				 * Updates actual new time to leave 
//				 * so calculations don't get fucked up
//				 */
//				dateTimeToLeave = dateFormatter.parse(Integer.toString(hourOfDay) + ":" + Integer.toString(minute));
//			} catch (ParseException e) {
//				Log.e("parakeet",e.getStackTrace().toString());
//			}
			
			calculateWakeUpTime();	//Calculate the new time to wake 
									//up according to new timeToLeave
		}
	};

	public void chooseTime() {
	
		firstLaunchHint.setVisibility(View.GONE);
		timeToWakeUp.setVisibility(View.VISIBLE);
		
		new TimePickerDialog(ParakeetMain.this, timePickerListener,
				config.getDateTimeToLeave().getHours(),
				config.getDateTimeToLeave().getMinutes(), true).show();
	}

	private void updateLabel() {
		timeToLeave.setText(config.getDateFormattedDate(config.getDateTimeAsDate(), FORMAT_TYPE_DATE));
	}

	/*
	 * Gets saved thingsToDo and times from memory, if any
	 */
	private void checkSavedTimes() {
		// Preferences
		final SharedPreferences settings = getPreferences(0);

		/*
		 * Checks if time to leave and time to wake up have been saved by old
		 * sessions
		 */
		try {
			if (settings.getLong("dateTimeToWakeUp", 0) == 0) {
//				Log.i("parakeet", "dateTimeToWakeUp non settato");
				firstLaunchHint.setVisibility(View.VISIBLE);
				timeToWakeUp.setVisibility(View.GONE);
				config.resetTimeToWakeUp();
				timeToWakeUp.setText(config.getDateFormattedDate(config.getDateTimeToWakeUp(), FORMAT_TYPE_DATE));
			} else {
//				Log.i("parakeet",
//						"dateTimeToWakeUp vale "
//								+ dateFormatter.format(dateTimeToWakeUp));
				firstLaunchHint.setVisibility(View.GONE);
				timeToWakeUp.setVisibility(View.VISIBLE);
				config.setDateTimeToWakeUp(new Date(settings.getLong("dateTimeToWakeUp",  0)));
				timeToWakeUp.setText(config.getDateFormattedDate(config.getDateTimeToWakeUp(), FORMAT_TYPE_DATE));
			}

			if (settings.getLong("dateTimeToLeave", 0) == 0) {
//				Log.i("parakeet", "dateTimeToLeave non settato");
				config.resetTimeToLeave();
				timeToLeave.setText(config.getDateFormattedDate(config.getDateTimeToLeave(), FORMAT_TYPE_DATE));
			} else {
//				Log.i("parakeet",
//						"dateTimeToLeave vale "
//								+ dateFormatter.format(dateTimeToLeave));
				config.setDateTimeToLeave(new Date(settings.getLong("dateTimeToLeave",
						0)));
				timeToLeave.setText(config.getDateFormattedDate(config.getDateTimeToLeave(), FORMAT_TYPE_DATE));
			}
			
			if (!settings.getString("toDoTasks", "").equals(""))
			{
				listOfThingsToDo = (List<ThingToDo>) ObjectSerializer.deserialize(settings.getString("toDoTasks", ObjectSerializer.serialize(new LinkedList<ThingToDo>())));
				/*
				 * Iterate through list and save their data
				 */
				ListIterator itr = listOfThingsToDo.listIterator();
				
			    while(itr.hasNext())
			    {
			    	ThingToDo nextThing = (ThingToDo)itr.next();
//			    	Log.i("parakeet", "Carico dati: " + nextThing.toString());
			    }
			}
			
		} catch (ParseException e) {
			Log.i("parakeet", e.getStackTrace().toString());
		} catch (IOException e) {
			Log.i("parakeet", e.getStackTrace().toString());
		}
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		
		if(listOfThingsToDo == null)
		{
			checkSavedTimes();			
		}

//		EasyTracker.getInstance().activityStart(this);

	}
	
    @Override
    public void onStop() {
      super.onStop();
//      EasyTracker.getInstance().activityStop(this);  // Add this method.
      
	  final SharedPreferences settings = getPreferences(0);
      config.saveAllData(settings);
    }
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.actionbarsherlock.app.SherlockActivity#onOptionsItemSelected(android
	 * .view.MenuItem) Cosa succede se l'utente seleziona una voce di menu o
	 * dell'action bar?
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if (item.getItemId() == android.R.id.home) {
            drawerLayoutHelper.toggle();
            return true;
        }

		int i = item.getItemId();
		if (i == R.id.action_about) {
			Intent aboutIntent = new Intent(ParakeetMain.this, AboutActivity.class);
			startActivity(aboutIntent);

			drawerLayoutHelper.toggle();
			return false;
		} else {
			drawerLayoutHelper.toggle();
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.ilsecondodasinistra.parakeet.CustomAdapter.ThingToDoCallback#calculateWakeUpTime()
	 * Implementation of callback method called when a checkbox is pressed
	 * This method sets the right time for alarm by subtracting the sum of minutes
	 * of things-to-do from the timeToLeave variable.
	 */
	@Override
	public void calculateWakeUpTime() {
		
		config.resetTotalTime();
		config.calculateWakeUpTime();
		
	    	config.setDateTimeToWakeUp(new Date(config.getDateTimeToLeaveTimestamp() -
	    								config.getTotalTimeInMillis()));
//			Toast.makeText(getBaseContext(), "Devi uscire alle " + dateFormatter.format(dateTimeToLeave) + " mentre devi alzarti alle " + 
//											dateFormatter.format(dateTimeToWakeUp) + 
//											" perché devi fare cose per un totale di " + 
//											Integer.toString(totalTime) + " minuti.", 
//											3000).show();
	    	timeToWakeUp.setText(config.getDateFormattedDate(config.getDateTimeToWakeUp(), FORMAT_TYPE_DATE));
	}
	
    @Override
    public void onClick(View v) {
    	
    	String name;
    	String length;

		int i = v.getId();
		if (i == R.id.button_cancel) {
			dialog.dismiss();

		} else if (i == R.id.button_add) {
			name = ((EditText) dialog.findViewById(R.id.name)).getText().toString();
			length = ((EditText) dialog.findViewById(R.id.length)).getText().toString();
			if (null != name
					&& 0 != name.compareTo("")
					&& null != length
					&& 0 != length.compareTo("")) {
				listOfThingsToDo.add(new ThingToDo(name, Integer.parseInt(length), true));
				listCustomAdapter.notifyDataSetChanged();
				calculateWakeUpTime();
				dialog.dismiss();
			}

		} else if (i == R.id.button_modify) {
			name = ((EditText) dialog.findViewById(R.id.name)).getText().toString();
			length = ((EditText) dialog.findViewById(R.id.length)).getText().toString();
			if (null != name
					&& 0 != name.compareTo("")
					&& null != length
					&& 0 != length.compareTo("")) {
				int position = (Integer) v.getTag();    //The tag we assigned to the button is actually the position
				listOfThingsToDo.get(position).setName(name);
				listOfThingsToDo.get(position).setLength(Integer.parseInt(length));
				listCustomAdapter.notifyDataSetChanged();
				calculateWakeUpTime();
				dialog.dismiss();
			}

		}
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerLayoutHelper = new DrawerLayoutHelper(ParakeetMain.this, actionBar);
        if(drawerLayoutHelper != null && drawerLayoutHelper.getDrawerToggle() != null)
        	drawerLayoutHelper.getDrawerToggle().syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerLayoutHelper.getDrawerToggle().onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {

            drawerLayoutHelper.toggle();

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /*
     * Action called when the user clicks on the "set alarm" button
     * in the action bar or in the activity itself
     */
    public void setAlarm()
    {
		/*
		 * Sets notification when it's time to leave
		 * Just in case you got stuck reading at the toilet
		 */
		boolean shouldThisBeNotified = prefs.getBoolean("notification_on_exit", true);
		
		Date moment = new Date(config.getDateTimeToLeaveTimestamp());
		if(shouldThisBeNotified)
//		if(config.getDateTimeToLeaveTimestamp() > now.getTime())
		{
			Intent notiIntent = new Intent(getBaseContext(), com.ilsecondodasinistra.parakeet.NotificationService.class);
			PendingIntent pi = PendingIntent.getService(getBaseContext(), 0, notiIntent, 0);
			AlarmManager mAlarm = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);
			
			Date now = new Date();
			/* Bugfix: If the date in config is in the past, change it to tomorrow.
			 * Not good code, I hope once I switch to Joda Time this will be better */
			if (config.getDateTimeToLeave().getTime() < now.getTime()) {
				//Instantiate calendars for the LeavingDate and today's date
				Calendar c = Calendar.getInstance();
				Calendar nowCal = Calendar.getInstance();
				nowCal.setTime(now);
				c.setTime(config.getDateTimeToLeave());
				
				//Sets today's date on LeavingDate and adds 1
				c.set(Calendar.DAY_OF_YEAR, nowCal.get(Calendar.DAY_OF_YEAR)+1);
				c.set(Calendar.YEAR, nowCal.get(Calendar.YEAR));
				
				//Saves obtained date
				config.setDateTimeToLeave(c.getTime());
			}
			
			mAlarm.set(AlarmManager.RTC_WAKEUP, config.getDateTimeToLeaveTimestamp(), pi);
		}
		
//		Toast.makeText(ParakeetMain.this, "Notification set at "+dateAndTimeFormatter.format(dateTimeToLeave.getTime()), 5000).show();
		
		//Sets alarm using external application
		Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); 
		i.putExtra(AlarmClock.EXTRA_MESSAGE, "New Alarm"); 
		i.putExtra(AlarmClock.EXTRA_HOUR, config.getDateTimeToWakeUp().getHours()); 
		i.putExtra(AlarmClock.EXTRA_MINUTES, config.getDateTimeToWakeUp().getMinutes()); 
		startActivity(i);
    }
    
    /*
     * Deletes all notifications
     */
    private void deleteAllNotifications() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancelAll(); //When application is open all its notifications must be deleted
		
		Intent i = new Intent(getBaseContext(), NotificationService.class);
		PendingIntent pi = PendingIntent.getService(getBaseContext(), 0, i, 0);
		AlarmManager mAlarm = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);

		if(pi != null) {
			mAlarm.cancel(pi);
			pi.cancel();
		}
    }
    
//    void showHelpIfNeeded(int type) {
//		/*
//		 * Toggles handler for application drawer
//		 */
//        drawerLayoutHelper = new DrawerLayoutHelper(ParakeetMain.this, actionBar);
//
//        ShowcaseViews views = new ShowcaseViews(this, new ShowcaseViews.OnShowcaseAcknowledged() {
//            @Override
//            public void onShowCaseAcknowledged(ShowcaseView showcaseView) {
//                /*
//                 * If application drawer was never opened manually,
//                 * automatically open it at first application run
//                 */
//        		final SharedPreferences settings = getPreferences(0);
//        		if (settings.getBoolean("drawerFirstOpening", true))
//        		{
//        			drawerLayoutHelper.toggle();
//
//        			SharedPreferences.Editor editor = settings.edit();
//        			editor.putBoolean("drawerFirstOpening", false);
//        			editor.commit();
//        		}
//            }
//        });
//
//        mOptions.block = false;
//
//		ShowcaseView.ConfigOptions options = new ShowcaseView.ConfigOptions();
////		options.shotType = ShowcaseView.TYPE_NO_LIMIT;
//		options.shotType = type;
//		options.showcaseId = 1234;
//
//		views.addView(new ItemViewProperties(R.id.time_to_leave_label,
//				R.string.time_to_leave_help_1,
//				R.string.time_to_leave_help_2,
//				options));
//
//		ShowcaseView.ConfigOptions showcaseConfigOptions = new ShowcaseView.ConfigOptions();
//        showcaseConfigOptions.block = true;
//        showcaseConfigOptions.shotType = type;
//        showcaseConfigOptions.fadeInDuration = 200;
//        showcaseConfigOptions.fadeOutDuration = 200;
//		showcaseConfigOptions.hideOnClickOutside = false;
//		showcaseConfigOptions.showcaseId = 1235;
//
//		views.addView(new ItemViewProperties(R.id.new_thing,
//				R.string.new_thing_help_1,
//				R.string.new_thing_help_2,
//				showcaseConfigOptions));
//
//		ShowcaseView.ConfigOptions showcaseConfigOptions3 = new ShowcaseView.ConfigOptions();
//        showcaseConfigOptions3.block = true;
//        showcaseConfigOptions3.shotType = type;
//        showcaseConfigOptions3.fadeInDuration = 200;
//        showcaseConfigOptions3.fadeOutDuration = 200;
//		showcaseConfigOptions3.hideOnClickOutside = false;
//		showcaseConfigOptions3.showcaseId = 1237;
//
//		views.addView(new ItemViewProperties(R.id.todoList,
//				R.string.handle_todo_help_1,
//				R.string.handle_todo_help_2,
//				showcaseConfigOptions3));
//
//		ShowcaseView.ConfigOptions showcaseConfigOptions2 = new ShowcaseView.ConfigOptions();
//        showcaseConfigOptions2.block = true;
//        showcaseConfigOptions2.shotType = type;
//        showcaseConfigOptions2.fadeInDuration = 200;
//        showcaseConfigOptions2.fadeOutDuration = 200;
//		showcaseConfigOptions2.hideOnClickOutside = false;
//		showcaseConfigOptions2.showcaseId = 1236;
//
//		views.addView(new ItemViewProperties(R.id.set_alarm,
//				R.string.set_alarm_help_1,
//				R.string.set_alarm_help_2,
//				showcaseConfigOptions2));
//
//		views.show();
////        ViewTarget target = new ViewTarget(R.id.set_alarm, this);
////        showcaseView = ShowcaseView.insertShowcaseView(target, this, R.string.string_1, R.string.string_2, showcaseConfigOptions);
//    }
}
