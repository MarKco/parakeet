package com.ilsecondodasinistra.parakeet;

import java.io.Serializable;

/**
 * LEGACY (v3.x). Kept byte-for-byte compatible (same package, name and fields) ONLY to
 * deserialize the task list saved by old versions. Do not use in new code.
 */
public class ThingToDo implements Serializable{
	
	private String name;
	private int length;
	private boolean checked;
	
	public ThingToDo(String inputName,
					int inputLength,
					boolean inputChecked) {
		
			this.name = inputName;
			this.length = inputLength;
			this.checked = inputChecked;
	}
	
	public void setName(String inputName) {
		this.name = inputName;
	}
	
	public void setLength(int inputLength) {
		this.length = inputLength;
	}
	
	public void setChecked(boolean inputChecked) {
		this.checked = inputChecked;
	}
	
	public String getName() {
		return this.name;
	}
	
	public int getLength() {
		return this.length;
	}
	
	public String getStringLength() {
		return Integer.toString(this.length);
	}
	
	public boolean getChecked() {
		return this.checked;
	}
	
	public String toString() {
		if(this.checked)
		{
			return this.name + " dura " + this.length + " e true";
		}
		else
		{
			return this.name + " dura " + this.length + " e false";
		}
	}

}
