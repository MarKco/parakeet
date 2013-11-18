package com.ilsecondodasinistra.parakeet;

import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import android.content.SharedPreferences;

import com.google.analytics.tracking.android.Log;

public class ParakeetConfig {

	private final int FORMAT_TYPE_DATE = 0;
	private final int FORMAT_TYPE_MINUTES = 1;
	private final int FORMAT_TYPE_DATE_COMPLETE = 2;
	
	int totalTime = 0;	//Tempo totale necessario per compleare le attività selezionate

	private Date dateTimeToWakeUp;
	private Date dateTimeToLeave;

	Calendar dateTime = GregorianCalendar.getInstance();

	private int hourToWake;
	private int minutesToWake;
	
	private List<ThingToDo> listOfThingsToDo;

	private SimpleDateFormat dateFormatter = new SimpleDateFormat("H:mm");;
	private SimpleDateFormat minutesFormatter = new SimpleDateFormat("mm");
	private SimpleDateFormat dateAndTimeFormatter = new SimpleDateFormat("yyyy-MM-dd, H:mm");
	
	public void ParakeetConfig() {
		dateTimeToLeave = new Date(0);
		dateTimeToWakeUp = new Date(0);
	}
	
	/*
	 * Returns given date formatted with specified formatter
	 */
	public String getDateFormattedDate(Date dateToFormat, int formatType)
	{
		switch(formatType)
		{
			case FORMAT_TYPE_DATE:
				return dateFormatter.format(dateToFormat.getTime());
			case FORMAT_TYPE_MINUTES:
				return minutesFormatter.format(dateToFormat.getTime());
			default:
				//Default is complete date format
				return dateAndTimeFormatter.format(dateToFormat.getTime());
		}
	}
	
	/*
	 * Parses given string with given formatter
	 * and returns the Date object
	 */
	public Date parseFormattedString(String stringToFormat, int formatType) {
		try {
			switch(formatType)
			{
				case FORMAT_TYPE_DATE:
					return dateFormatter.parse(stringToFormat);
				case FORMAT_TYPE_MINUTES:
					return minutesFormatter.parse(stringToFormat);
				default:
					//Default is complete date format
					return dateAndTimeFormatter.parse(stringToFormat);
			}
		}
		catch(ParseException e)
		{
			Log.i("Date parsing exception. Date:" + stringToFormat);
			return new Date();
		}
	}
	
	
	public Date getDateTimeToWakeUp() {
		return dateTimeToWakeUp;
	}
	public void setDateTimeToWakeUp(Date dateTimeToWakeUp) {
		this.dateTimeToWakeUp = dateTimeToWakeUp;
	}
	public Date getDateTimeToLeave() {
		return dateTimeToLeave;
	}
	public long getDateTimeToLeaveTimestamp() {
		return dateTimeToLeave.getTime();
	}
	public void setDateTimeToLeave(Date dateTimeToLeave) {
		this.dateTimeToLeave = dateTimeToLeave;
	}
	public int getHourToWake() {
		return hourToWake;
	}
	public void setHourToWake(int hourToWake) {
		this.hourToWake = hourToWake;
	}
	public Calendar getDateTime() {
		return dateTime;
	}
	
	public Date getDateTimeAsDate() {
		return dateTime.getTime();
	}
	
	public void setDateTime(Calendar dateTime) {
		this.dateTime = dateTime;
	}
	public void setDateTimeTo(int parameter, int value)
	{
		this.dateTime.set(parameter, value);
	}
	public String getDateTimeFormatted(long getTime) {
		return dateFormatter.format(dateTime.getTime());
	}

	public int getTotalTime() {
		return totalTime;
	}
	
	public int getTotalTimeInMillis() {
		 return (totalTime * 60 * 1000);
	}

	public void setTotalTime(int totalTime) {
		this.totalTime = totalTime;
	}

	public List<ThingToDo> getListOfThingsToDo() {
		return listOfThingsToDo;
	}

	public void setListOfThingsToDo(List<ThingToDo> listOfThingsToDo) {
		this.listOfThingsToDo = listOfThingsToDo;
	}

	public void resetTimeToWakeUp() throws ParseException
	{
		dateTimeToWakeUp = dateFormatter.parse("0:00");
	}
	
	public void resetTimeToLeave() throws ParseException
	{
		dateTimeToLeave = dateFormatter.parse("0:00");
	}
	
	public void resetTotalTime()
	{
		totalTime = 0;
	}
	
	/*
	 * Adds to total time the length of
	 * a single activity todo
	 */
	public void addTotalTime(int timeToAdd)
	{
		totalTime += timeToAdd;
	}
	
	/*
	 * This method returns a new list of things-to-do.
	 * It's called the first time the application is opened and
	 * therefore the list must be created by the app itself
	 */
	public List<ThingToDo> getNewListOfThingsToDo() {
		listOfThingsToDo = new LinkedList<ThingToDo>();

		if(Locale.getDefault().getLanguage() != null
				&&
		   Locale.getDefault().getLanguage().equals("it"))
		{
//			Toast.makeText(getBaseContext(), "Carico tutto da capo", 1000).show();
	
			listOfThingsToDo.add(new ThingToDo("doccia", 20, false));
			listOfThingsToDo.add(new ThingToDo("slavazzamento", 10, false));
			listOfThingsToDo.add(new ThingToDo("vestirsi", 10, false));
			listOfThingsToDo.add(new ThingToDo("apparecchia colazione", 3, false));
			listOfThingsToDo.add(new ThingToDo("colazione", 20, false));
			listOfThingsToDo.add(new ThingToDo("barba", 10, false));
			listOfThingsToDo.add(new ThingToDo("denti", 5, false));
			listOfThingsToDo.add(new ThingToDo("spazzatura", 3, false));
			listOfThingsToDo.add(new ThingToDo("porta fuori macchina", 10, false));
			listOfThingsToDo.add(new ThingToDo("porta fuoribici", 2, false));
			listOfThingsToDo.add(new ThingToDo("pranzo al sacco", 5, false));
			listOfThingsToDo.add(new ThingToDo("cane", 30, false));
		}
		else
		{
			listOfThingsToDo.add(new ThingToDo("shower", 20, false));
			listOfThingsToDo.add(new ThingToDo("washing", 10, false));
			listOfThingsToDo.add(new ThingToDo("prepare breakfast", 7, false));
			listOfThingsToDo.add(new ThingToDo("getting dressed", 10, false));
			listOfThingsToDo.add(new ThingToDo("breakfast", 20, false));
			listOfThingsToDo.add(new ThingToDo("beard", 10, false));
			listOfThingsToDo.add(new ThingToDo("teeth", 5, false));
			listOfThingsToDo.add(new ThingToDo("garbage", 3, false));
			listOfThingsToDo.add(new ThingToDo("get car", 10, false));
			listOfThingsToDo.add(new ThingToDo("get bike", 2, false));
			listOfThingsToDo.add(new ThingToDo("lunchbox", 5, false));
			listOfThingsToDo.add(new ThingToDo("dog", 30, false));				
		}
		
		return listOfThingsToDo;
	}

	public void calculateWakeUpTime()
	{
		/*
		 * Iterate through list and get the sum 
		 * of timings for each action
		 */
		ListIterator itr = listOfThingsToDo.listIterator();
		
	    while(itr.hasNext())
	    {
	    	ThingToDo nextThing = (ThingToDo)itr.next();
	    	if(nextThing.getChecked())
	    	{
		    	addTotalTime(nextThing.getLength());
	    	}
	    }
	}
	
	/*
	 * This method saves in the preferences
	 * all data of this configuration
	 */
	public void saveAllData(SharedPreferences settings)
	{
		SharedPreferences.Editor editor = settings.edit();
		try {
			editor.putLong("dateTimeToWakeUp", dateTimeToWakeUp.getTime());
//			editor.putLong("dateTimeToWakeUp",
//					parseFormattedString(timeToWakeUp.getText().toString(), FORMAT_TYPE_DATE).getTime());
			editor.putLong("dateTimeToLeave", dateTimeToLeave.getTime());
//			editor.putLong("dateTimeToLeave",
//					parseFormattedString(timeToLeave.getText().toString(), FORMAT_TYPE_DATE).getTime());
			
			editor.putString("toDoTasks","");
	        editor.putString("toDoTasks", ObjectSerializer.serialize((Serializable) listOfThingsToDo));

			/*
			 * Iterate through list and save their data
			 */
			ListIterator itr = listOfThingsToDo.listIterator();
			
		    while(itr.hasNext())
		    {
		    	ThingToDo nextThing = (ThingToDo)itr.next();
//		    	Log.i("parakeet", "Salvo: " + nextThing.toString());
		    }
	        
		} catch (IOException e) {
			e.printStackTrace();
//			Log.e("parakeet", e.getStackTrace().toString());
		}
	
		editor.commit();
	}

}