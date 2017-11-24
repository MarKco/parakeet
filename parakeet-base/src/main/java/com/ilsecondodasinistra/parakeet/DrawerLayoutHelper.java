package com.ilsecondodasinistra.parakeet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.ilsecondodasinistra.parakeet.shared.AboutActivity;
import com.ilsecondodasinistra.parakeet.shared.SettingsActivity;

public class DrawerLayoutHelper {

    private ParakeetMain activity;

    private ActionBar actionBar;

    private DrawerLayout drawerLayout;

    private ListView drawerListView;

    private ActionBarDrawerToggle drawerToggle;

    private int defaultActionBarDisplay;

    private int defaultNavigationMode;

    private CharSequence defaultTitle;

    public DrawerLayoutHelper(final ParakeetMain activity, final ActionBar actionBar) {

        this.activity = activity;
        this.actionBar = actionBar;

        /*
         * dati di default dell'action bar
         */
        defaultNavigationMode = actionBar.getNavigationMode();
        defaultTitle = actionBar.getTitle();
        defaultActionBarDisplay = actionBar.getDisplayOptions();

        drawerLayout = (DrawerLayout)activity.findViewById(R.id.main_drawer);
//        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        drawerListView = (ListView)activity.findViewById(R.id.left_drawer);

        /*
         * qui imposti l'adapter per la lista
         */
        // Creating an ArrayAdapter to add items to the listview mDrawerList
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            activity.getBaseContext(),
            R.layout.drawer_list_item,
            activity.getResources().getStringArray(R.array.drawer_items)
        );
        
        drawerListView.setAdapter(adapter);
        
        // Set the list's click listener
        drawerListView.setOnItemClickListener(new DrawerItemClickListener());
        
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        drawerToggle = new ActionBarDrawerToggle(activity, drawerLayout, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerClosed(View view) {
            	
                restoreActionBar();
            }
            
            public void onDrawerOpened(View drawerView) {
                actionBar.setTitle(activity.getString(R.string.title_activity_settings));
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            }
        };
        drawerLayout.setDrawerListener(drawerToggle);
    }

	public ActionBarDrawerToggle getDrawerToggle() {
        return drawerToggle;
    }

    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }
    
    public void toggle() {
        if (drawerLayout.isDrawerOpen(drawerListView)) {
            drawerLayout.closeDrawer(drawerListView);
        }
        else {
            drawerLayout.openDrawer(drawerListView);
        }
    }

    private void restoreActionBar() {
        actionBar.setCustomView(null);
//        actionBar.setDisplayOptions(defaul<tActionBarDisplay); //Removes the three dots on the left. I like them!
        actionBar.setHomeButtonEnabled(true);
        actionBar.setNavigationMode(defaultNavigationMode);
        actionBar.setTitle(defaultTitle);
    }
    
    public class DrawerItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    /** Swaps fragments in the main content view */
    private void selectItem(int position) {
        // Create a new fragment and specify the planet to show based on position

        switch (position) {
		case 0:
			//Button "set alarm"
			this.activity.setAlarm();
			break;
		case 1:
			//Button "Set time to leave"
			this.activity.chooseTime();
			break;
		case 2:
			//Reset notification on exit custom text
		    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.activity);
		    SharedPreferences.Editor editor = prefs.edit();
		    editor.putString("exit_noti_text", "");
		    editor.commit();
		    Toast.makeText(this.activity, this.activity.getString(R.string.reset_noti_toast), 1000).show();
			break;
		case 3:
			//Start Settings Activity
			Intent i = new Intent(this.activity, SettingsActivity.class);
			this.activity.startActivity(i);
//			this.activity.sendMail();
			break;
		case 4:
			Intent aboutIntent = new Intent(this.activity, AboutActivity.class);
			this.activity.startActivity(aboutIntent);
			break;
//		case 5:
//			this.activity.showHelpIfNeeded(ShowcaseView.TYPE_NO_LIMIT);
//			break;
		default:
			break;
		}

        // Highlight the selected item, update the title, and close the drawer
        drawerListView.setItemChecked(position, true);
        drawerLayout.closeDrawer(drawerListView);
        
    }    
}
