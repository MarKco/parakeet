package com.ilsecondodasinistra.parakeet;

import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.TextView;

public class CustomAdapter extends ArrayAdapter<ThingToDo> {
	
	private ThingToDoCallback callback;

	public CustomAdapter(Context context, int resource, List<ThingToDo> objects) {
		super(context, resource, objects);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getViewOptimize(position, convertView, parent);
	}

	public View getViewOptimize(int position, View convertView, ViewGroup parent) {
		ViewHolder viewHolder = null;
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.row, null);

			viewHolder = new ViewHolder();
			viewHolder.todoName = (TextView) convertView
					.findViewById(R.id.todo_name);
			viewHolder.todoLength = (TextView) convertView
					.findViewById(R.id.todo_length);
			viewHolder.todoChecked = (CheckBox) convertView
					.findViewById(R.id.todo_todo);

			convertView.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) convertView.getTag();
		}
		
		Typeface tf = Typeface.createFromAsset(getContext().getAssets(),
				"fonts/digital-7.ttf");

		viewHolder.todoLength.setTypeface(tf);
		
		if (position % 2 == 0)
		{
			convertView.setBackgroundColor(0x33a9c606);
		}
		else
		{
			convertView.setBackgroundColor(0x11CCFFCC);
		}

		final ThingToDo toDoItem = getItem(position);
		
		viewHolder.todoName.setText(toDoItem.getName());
		viewHolder.todoLength.setText(toDoItem.getStringLength());

		/*
		 * Il setOnCheckedChangeListener DEVE andare prima del setChecked
		 * altrimenti il metodo setChecked, che RICHIAMA onCheckedChangeListener,
		 * chiamerebbe il vecchio listener, non eseguendo il codice qui sotto
		 * e creando quindi dei casini molto difficili da individuare, come ad
		 * esempio le checkbox che non si aggiornano quando fai scroll della lista
		 */
		viewHolder.todoChecked
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton arg0,
							boolean isChecked) {
	                    if(callback != null) {
	                    	if(isChecked)
	                    		toDoItem.setChecked(true);
	                    	else
	                    		toDoItem.setChecked(false);
//                			Toast.makeText(getContext(), "L'elemento " + toDoItem.getName() + " per domani è " + isChecked, 200).show();
//                			Log.i("parakeet","L'elemento " + toDoItem.getName() + " per domani è " + isChecked);
	                        callback.calculateWakeUpTime();
	                     }
					}
				});
		
		viewHolder.todoChecked.setChecked(toDoItem.getChecked());
		
		return convertView;
	}

	public void setCallback(ThingToDoCallback callback) {

		this.callback = callback;
	}
	
    public interface ThingToDoCallback {
        public void calculateWakeUpTime();

    }

	private class ViewHolder {
		public TextView todoName;
		public TextView todoLength;
		public CheckBox todoChecked;
	}

}
