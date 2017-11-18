package com.ilsecondodasinistra.parakeet;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.AlarmClock;
import android.widget.Toast;

public class OnBootReceiver extends BroadcastReceiver {
  public static void setAlarm(Context ctxt) {
    AlarmManager mgr=(AlarmManager)ctxt.getSystemService(Context.ALARM_SERVICE);
    Calendar cal=Calendar.getInstance();
    
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(ctxt);
    int hour = prefs.getInt("alarmHour", 8);
    int minutes = prefs.getInt("alarmMinutes", 0);
    
    cal.set(Calendar.HOUR_OF_DAY, hour);
    cal.set(Calendar.MINUTE, minutes - 1);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    
    if (cal.getTimeInMillis()<System.currentTimeMillis()) {
      cal.add(Calendar.DAY_OF_YEAR, 1);
    }
    
    mgr.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                      getPendingIntent(ctxt));
    
    Toast.makeText(ctxt, "Piazzato!", 200).show();    
    
    //Sets real alarm
//	Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); 
//	i.putExtra(AlarmClock.EXTRA_MESSAGE, "New Alarm"); 
//	i.putExtra(AlarmClock.EXTRA_HOUR, hour); 
//	i.putExtra(AlarmClock.EXTRA_MINUTES, minutes);
//	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//	ctxt.startActivity(i);

  }
  
  public static void cancelAlarm(Context ctxt) {
    AlarmManager mgr=(AlarmManager)ctxt.getSystemService(Context.ALARM_SERVICE);

    mgr.cancel(getPendingIntent(ctxt));
  }
  
  private static PendingIntent getPendingIntent(Context ctxt) {
    Intent i=new Intent(ctxt, OnAlarmReceiver.class);
    
    return(PendingIntent.getBroadcast(ctxt, 0, i, 0));
  }
  
  @Override
  public void onReceive(Context ctxt, Intent intent) {
    setAlarm(ctxt);
  }
}