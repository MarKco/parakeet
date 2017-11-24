package com.ilsecondodasinistra.parakeet.shared;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.instantapps.InstantApps;

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

		Button installButton = (Button)findViewById(R.id.install);

        if (!InstantApps.isInstantApp(AboutActivity.this)) {
            installButton.setVisibility(View.GONE);
        }

		installButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
                Intent toOtherActivityIntent = new Intent();
                toOtherActivityIntent.setAction("com.ilsecondodasinistra.parakeet.SETTINGS");

                if (InstantApps.isInstantApp(AboutActivity.this)) {
					InstantApps.showInstallPrompt(AboutActivity.this, toOtherActivityIntent, 0, "B");
				} else {
					startActivity(toOtherActivityIntent);
				}
			}
		});
	}

}
