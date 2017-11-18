package com.ilsecondodasinistra.parakeet;

import java.util.Calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.AlarmClock;

public class OnAlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context ctxt, Intent intent) {
//	OnBootReceiver.cancelAlarm(ctxt);
	  
	Calendar c = Calendar.getInstance(); 
	  
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(ctxt);
    int hour = prefs.getInt("alarmHour", 8);
    int minutes = prefs.getInt("alarmMinutes", 0);
	  
	Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); 
	i.putExtra(AlarmClock.EXTRA_MESSAGE, "New Alarm"); 
	i.putExtra(AlarmClock.EXTRA_HOUR, hour);
	i.putExtra(AlarmClock.EXTRA_MINUTES, minutes);
	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	ctxt.startActivity(i);
	  
//    Intent i=new Intent(ctxt, ParakeetMain.class);
//    
//    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//    
//    ctxt.startActivity(i);
  }
}