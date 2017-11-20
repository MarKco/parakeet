package com.ilsecondodasinistra.parakeet.shared;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class AboutActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		TextView emailTextView = (TextView) findViewById(R.id.authorEmailTV);
		emailTextView.setMovementMethod(LinkMovementMethod.getInstance());

		TextView appInfoTextView = (TextView) findViewById(R.id.appInfoTV);
		appInfoTextView.setMovementMethod(LinkMovementMethod.getInstance());

		TextView appAuthorTextView = (TextView) findViewById(R.id.appAuthorTV);
		appAuthorTextView.setMovementMethod(LinkMovementMethod.getInstance());

		// ATTENTION: This was auto-generated to handle app links.
		Intent appLinkIntent = getIntent();
		String appLinkAction = appLinkIntent.getAction();
		Uri appLinkData = appLinkIntent.getData();
	}

}
