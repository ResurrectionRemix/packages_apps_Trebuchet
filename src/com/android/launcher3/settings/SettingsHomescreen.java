/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.settings;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.lineage.LineageLauncherCallbacks;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.SecureSettingsObserver;
import android.os.Process;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceFragment.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragment.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsHomescreen extends Activity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    public static final String KEY_MINUS_ONE = "pref_enable_minus_one";

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new HomescreenSettingsFragment()).commit();
        }
        Utilities.getPrefs(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case KEY_MINUS_ONE:
            case Utilities.SHOW_WORKSPACE_GRADIENT:
            case Utilities.SHOW_HOTSEAT_GRADIENT:
            case Utilities.DESKTOP_SHOW_QUICKSPACE:
                 showSnackBar();
                break;
            case Utilities.KEY_SHOW_ALT_QUICKSPACE:
                 showSnackBar();
                break;
            case Utilities.KEY_SHOW_QUICKSPACE_NOWPLAYING:
            case Utilities.KEY_SHOW_QUICKSPACE_PSONALITY:
                LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                showSnackBar();
                break;
            default:
                break;
        }
    }

    private void showSnackBar() {
         View rootView = findViewById(android.R.id.content);
         if (rootView != null) {
             Snackbar snackbar  =Snackbar.make(rootView,
                 R.string.restart_archived, Snackbar.LENGTH_LONG);

             snackbar.setAction(R.string.restart_warning, new RestartLauncher())
                .setActionTextColor(getApplicationContext().getResources().getColor(com.android.internal.R.color.accent_device_default_light))
                .show();
                //Change the Snackbar default text color
                View snackbarView = snackbar.getView();
                TextView tv = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
                tv.setTextColor(Color.WHITE);
          }
    }

   public class RestartLauncher implements View.OnClickListener {

       @Override
       public void onClick(View v) {
          Process.killProcess(Process.myPid());
       }
    }


    private boolean startFragment(String fragment, Bundle args, String key) {
        if (Utilities.ATLEAST_P && getFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new fragments in that case.
            return false;
        }
        Fragment f = Fragment.instantiate(this, fragment, args);
        if (f instanceof DialogFragment) {
            ((DialogFragment) f).show(getFragmentManager(), key);
        } else {
            getFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, f)
                    .addToBackStack(key)
                    .commit();
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragment preferenceFragment, Preference pref) {
        return startFragment(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
        return startFragment(getString(R.string.home_category_title), args, pref.getKey());
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class HomescreenSettingsFragment extends PreferenceFragment {

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.home_screen_preferences, rootKey);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                if (!initPreference(preference)) {
                    screen.removePreference(preference);
                }
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        protected String getParentKeyForPref(String key) {
            return null;
        }

        /**
         * Initializes a preference. This is called for every preference. Returning false here
         * will remove that preference from the list.
         */
        protected boolean initPreference(Preference preference) {
            switch (preference.getKey()) {
                case KEY_MINUS_ONE:
                    return LineageUtils.hasPackageInstalled(getActivity(),
                            LineageLauncherCallbacks.SEARCH_PACKAGE);
            }

            return true;
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(list, position) : null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }
}
