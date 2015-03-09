/*
 * Copyright 2015 Ryan Jones
 *
 * This file is part of smartcard-reader, package org.docrj.smartcard.reader.
 *
 * smartcard-reader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * smartcard-reader is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with smartcard-reader. If not, see <http://www.gnu.org/licenses/>.
 */

package org.docrj.smartcard.reader;

import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;


public class AppEditActivity extends ActionBarActivity {

    private static final String TAG = LaunchActivity.TAG;

    // actions
    private static final String ACTION_NEW_APP = AppsListActivity.ACTION_NEW_APP;
    private static final String ACTION_VIEW_APP = AppsListActivity.ACTION_VIEW_APP;
    private static final String ACTION_EDIT_APP = AppsListActivity.ACTION_EDIT_APP;
    private static final String ACTION_COPY_APP = AppsListActivity.ACTION_COPY_APP;

    // extras
    private static final String EXTRA_APP_POS = AppsListActivity.EXTRA_APP_POS;

    private static final int NEW_APP_POS = -1;

    private SharedPreferences.Editor mEditor;
    private ArrayList<SmartcardApp> mApps;
    private String mAction;
    private int mAppPos;

    private EditText mName;
    private EditText mAid;
    private RadioGroup mType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_edit);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.actionbar_cancel).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // "cancel"
                        finish();
                    }
                });
        findViewById(R.id.actionbar_save).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // "save"
                        saveAndFinish(false);
                    }
                });

        // persistent data in shared prefs
        SharedPreferences ss = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        mEditor = ss.edit();

        String json = ss.getString("apps", null);
        if (json != null) {
            // deserialize list of SmartcardApp
            Gson gson = new Gson();
            Type collectionType = new TypeToken<ArrayList<SmartcardApp>>() {
            }.getType();
            mApps = gson.fromJson(json, collectionType);
        }

        Intent intent = getIntent();
        mAction = intent.getAction();
        mAppPos = intent.getIntExtra(EXTRA_APP_POS, NEW_APP_POS);

        mName = (EditText) findViewById(R.id.app_name);
        mAid = (EditText) findViewById(R.id.app_aid);
        mType = (RadioGroup) findViewById(R.id.radio_grp_type);

        TextView note = (TextView) findViewById(R.id.note);
        note.setVisibility(View.GONE);

        if (ACTION_EDIT_APP.equals(mAction) || ACTION_COPY_APP.equals(mAction)) {
            SmartcardApp app = mApps.get(mAppPos);
            mName.setText(app.getName());
            mAid.setText(app.getAid());
            mType.check((app.getType() == SmartcardApp.TYPE_OTHER) ? R.id.radio_other
                    : R.id.radio_payment);
        }
        if (ACTION_COPY_APP.equals(mAction)) {
            // now that we have populated the fields, treat copy like new
            mAppPos = NEW_APP_POS;
        }
        mName.requestFocus();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }
    }

    @Override
    public void onBackPressed() {
        saveAndFinish(true);
        super.onBackPressed();
    }

    private boolean validateNameAndAid(String name, String aid, boolean backPressed) {
        if (name.isEmpty()) {
            if (!(backPressed && mAction.equals(ACTION_NEW_APP) && aid.isEmpty())) {
                showToast(getString(aid.isEmpty() ?
                        R.string.empty_name_aid : R.string.empty_name));
            }
            return false;
        }
        if (aid.isEmpty()) {
            showToast(getString(R.string.empty_aid));
            return false;
        }
        if (aid.length() < 10 || aid.length() > 32
                || aid.length() % 2 != 0) {
            showToast(getString(R.string.invalid_aid));
            return false;
        }
        // ensure name is unique
        for (int i = 0; i < mApps.size(); i++) {
            // skip the app being edited
            if (mAppPos == i)
                continue;
            SmartcardApp app = mApps.get(i);
            if (app.getName().equals(name)) {
                showToast(getString(R.string.name_exists,
                        name));
                return false;
            }
        }
        return true;
    }

    private void saveAndFinish(boolean backPressed) {
        // validate name and aid
        String name = mName.getText().toString();
        String aid = mAid.getText().toString();
        if (!validateNameAndAid(name, aid, backPressed)) {
            return;
        }
        // app type radio group
        int selectedId = mType.getCheckedRadioButtonId();
        RadioButton radioBtn = (RadioButton) findViewById(selectedId);
        int type = radioBtn.getText().toString()
                .equals(getString(R.string.radio_payment)) ? SmartcardApp.TYPE_PAYMENT
                : SmartcardApp.TYPE_OTHER;

        boolean appChanged = false;
        SmartcardApp newApp = new SmartcardApp(name, aid, type);

        if (mAction == ACTION_NEW_APP || mAction == ACTION_COPY_APP) {
            appChanged = true;
            synchronized (mApps) {
                mApps.add(newApp);
            }
        } else { // edit app
            SmartcardApp app = mApps.get(mAppPos);
            if (!newApp.equals(app)) {
                appChanged = true;
                mApps.set(mAppPos, newApp);
            }
        }

        if (appChanged) {
            writePrefs();
        }
        if (appChanged || !backPressed) {
            showToast(getString(R.string.app_saved));
        }

        if (mAction == ACTION_COPY_APP) {
            Intent i = new Intent(this, AppViewActivity.class);
            i.setAction(ACTION_VIEW_APP);
            i.putExtra(EXTRA_APP_POS, mApps.size()-1);
            startActivity(i);
            // calling activity (copy-from app view) will finish
            setResult(RESULT_OK);
        }
        finish();
    }

    private void writePrefs() {
        // serialize list of SmartcardApp
        Gson gson = new Gson();
        String json = gson.toJson(mApps);
        mEditor.putString("apps", json);
        mEditor.commit();
    }

    private void showToast(String text) {
        Toast toast = Toast.makeText(this, text,
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, -100);
        toast.show();
    }
}
