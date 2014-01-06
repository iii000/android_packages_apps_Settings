/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.settings.cyanogenmod;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.io.File;

/**
 * Performance Settings
 */
public class PerformanceSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "PerformanceSettings";
    private static final String PERF_PROFILE_PREF = "performance_profile";
    private static final String USE_16BPP_ALPHA_PREF = "pref_use_16bpp_alpha";
    private static final String USE_16BPP_ALPHA_PROP = "persist.sys.use_16bpp_alpha";
    private static final String PURGEABLE_ASSETS_PREF = "pref_purgeable_assets";
    private static final String PURGEABLE_ASSETS_PERSIST_PROP = "persist.sys.purgeable_assets";
    private static final String PURGEABLE_ASSETS_DEFAULT = "0";

    private static String FREQ_CUR_FILE = SCALE_CUR_FILE;

    public static final String FREQ_CUR_PREF = "pref_cpu_freq_cur";
    public static final String SCALE_CUR_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
    public static final String FREQINFO_CUR_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq";
    public static final String GOV_PREF = "pref_cpu_gov";
    public static final String GOV_LIST_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors";
    public static final String GOV_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";
    public static final String FREQ_MIN_PREF = "pref_cpu_freq_min";
    public static final String FREQ_MAX_PREF = "pref_cpu_freq_max";
    public static final String FREQ_LIST_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    public static String FREQ_MAX_FILE = null;
    public static String FREQ_MIN_FILE = null;
    public static final String SOB_PREF = "pref_cpu_set_on_boot";
    public static final String KSM_RUN_FILE = "/sys/kernel/mm/ksm/run";
    public static final String KSM_PREF = "pref_ksm";
    public static final String KSM_PREF_DISABLED = "0";
    public static final String KSM_PREF_ENABLED = "1";
    public static final String IOSCHED_PREF = "pref_io_sched";
    public static final String IOSCHED_LIST_FILE = "/sys/block/mmcblk0/queue/scheduler";
    public static final String SOB_PREF = "pref_io_sched_set_on_boot";
    public static final String PERF_PROFILE_PREF = "pref_perf_profile";
    public static final String SOB_PREF = "pref_perf_profile_set_on_boot";

    protected static boolean freqCapFilesInitialized = false;

    private String mGovernorFormat;
    private String mMinFrequencyFormat;
    private String mMaxFrequencyFormat;
    private String mIOSchedulerFormat;
    private String mPerfProfileProp;
    private String mPerfProfileFormat;

    private String[] mPerfProfileEntries;
    private String[] mPerfProfileValues;

    private ListPreference mIOSchedulerPref;
    private ListPreference mPerfProfilePref;
    private ListPreference mGovernorPref;
    private ListPreference mMinFrequencyPref;
    private ListPreference mMaxFrequencyPref;

    private Preference mPerfProfilePref;
    private Preference mCurFrequencyPref;

    private CheckBoxPreference mUse16bppAlphaPref;
    private CheckBoxPreference mPurgeableAssetsPref;
    private CheckBoxPreference mKSMPref;

    private int swapAvailable = -1;

    private AlertDialog alertDialog;

    private class CurCPUThread extends Thread {
        private boolean mInterrupt = false;

        public void interrupt() {
            mInterrupt = true;
        }

        @Override
        public void run() {
            try {
                while (!mInterrupt) {
                    sleep(500);
                    final String curFreq = Utils.fileReadOneLine(FREQ_CUR_FILE);
                    if (curFreq != null)
                        mCurCPUHandler.sendMessage(mCurCPUHandler.obtainMessage(0, curFreq));
                }
            } catch (InterruptedException e) {
            }
        }
    };

    private CurCPUThread mCurCPUThread = new CurCPUThread();

    private Handler mCurCPUHandler = new Handler() {
        public void handleMessage(Message msg) {
            mCurFrequencyPref.setSummary(toMHz((String) msg.obj));
        }
    };

    private void initFreqCapFiles()
    {
        if (freqCapFilesInitialized) return;
        FREQ_MAX_FILE = getString(R.string.max_cpu_freq_file);
        FREQ_MIN_FILE = getString(R.string.min_cpu_freq_file);
        freqCapFilesInitialized = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.performance_settings);

        final Resources res = getResources();
        PreferenceScreen prefSet = getPreferenceScreen();

            mPerfProfilePref = prefSet.findPreference(PERF_PROFILE_PREF);
            String perfProfileProp = getString(R.string.config_perf_profile_prop);
        if (mPerfProfilePref != null && TextUtils.isEmpty(perfProfileProp)) {
            prefSet.removePreference(mPerfProfilePref);
        }

        mUse16bppAlphaPref = (CheckBoxPreference) prefSet.findPreference(USE_16BPP_ALPHA_PREF);
        String use16bppAlpha = SystemProperties.get(USE_16BPP_ALPHA_PROP, "0");
        mUse16bppAlphaPref.setChecked("1".equals(use16bppAlpha));

        /* Display the warning dialog */
        alertDialog = new AlertDialog.Builder(getActivity()).create();
        alertDialog.setTitle(R.string.performance_settings_warning_title);
        alertDialog.setMessage(getResources().getString(R.string.performance_settings_warning));
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                getResources().getString(com.android.internal.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        return;
                    }
                });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                PerformanceSettings.this.finish();
            }
        });
        alertDialog.show();

       // IO Scheduler

        mIOSchedulerFormat = getString(R.string.io_sched_summary);

        String[] availableIOSchedulers = new String[0];
        String availableIOSchedulersLine;
        int bropen, brclose;
        String currentIOScheduler = null;

        mIOSchedulerPref = (ListPreference) prefSet.findPreference(IOSCHED_PREF);

        /* I/O scheduler
        Some systems might not use I/O schedulers */
        if (!Utils.fileExists(IOSCHED_LIST_FILE) ||
            (availableIOSchedulersLine = Utils.fileReadOneLine(IOSCHED_LIST_FILE)) == null) {
            prefSet.removePreference(mIOSchedulerPref);

        } else {
            availableIOSchedulers = availableIOSchedulersLine.replace("[", "").replace("]", "").split(" ");
            bropen = availableIOSchedulersLine.indexOf("[");
            brclose = availableIOSchedulersLine.lastIndexOf("]");
            if (bropen >= 0 && brclose >= 0)
                currentIOScheduler = availableIOSchedulersLine.substring(bropen + 1, brclose);

            mIOSchedulerPref.setEntryValues(availableIOSchedulers);
            mIOSchedulerPref.setEntries(availableIOSchedulers);
            if (currentIOScheduler != null)
                mIOSchedulerPref.setValue(currentIOScheduler);
            mIOSchedulerPref.setSummary(String.format(mIOSchedulerFormat, currentIOScheduler));
            mIOSchedulerPref.setOnPreferenceChangeListener(this);
        }

        // Performance Profiles

        mPerfProfileProp = getString(R.string.config_perf_profile_prop);
        mPerfProfileFormat = getString(R.string.perf_profile_summary);

        mPerfProfileEntries = getResources().getStringArray(R.array.perf_profile_entries);
        mPerfProfileValues = getResources().getStringArray(R.array.perf_profile_values);

        mPerfProfilePref = (ListPreference) prefSet.findPreference(PERF_PROFILE_PREF);
        mPerfProfilePref.setOnPreferenceChangeListener(this);
        setCurrentPerfProfileSummary();
    
        // Memory Management

        mPurgeableAssetsPref = (CheckBoxPreference) prefSet.findPreference(PURGEABLE_ASSETS_PREF);
        mKSMPref = (CheckBoxPreference) prefSet.findPreference(KSM_PREF);

        if (Utils.fileExists(KSM_RUN_FILE)) {
            mKSMPref.setChecked(KSM_PREF_ENABLED.equals(Utils.fileReadOneLine(KSM_RUN_FILE)));
        } else {
            prefSet.removePreference(mKSMPref);
        }

        String purgeableAssets = SystemProperties.get(PURGEABLE_ASSETS_PERSIST_PROP,
                PURGEABLE_ASSETS_DEFAULT);
        mPurgeableAssetsPref.setChecked("1".equals(purgeableAssets));

        // Processor

        initFreqCapFiles();

        mGovernorFormat = getString(R.string.cpu_governors_summary);
        mMinFrequencyFormat = getString(R.string.cpu_min_freq_summary);
        mMaxFrequencyFormat = getString(R.string.cpu_max_freq_summary);

        String[] availableFrequencies = new String[0];
        String[] availableGovernors = new String[0];
        String[] frequencies;
        String availableGovernorsLine;
        String availableFrequenciesLine;
        String temp;

        mGovernorPref = (ListPreference) prefSet.findPreference(GOV_PREF);
        mCurFrequencyPref = (Preference) prefSet.findPreference(FREQ_CUR_PREF);
        mMinFrequencyPref = (ListPreference) prefSet.findPreference(FREQ_MIN_PREF);
        mMaxFrequencyPref = (ListPreference) prefSet.findPreference(FREQ_MAX_PREF);

        /* Governor
        Some systems might not use governors */
        if (!Utils.fileExists(GOV_LIST_FILE) || !Utils.fileExists(GOV_FILE) || (temp = Utils.fileReadOneLine(GOV_FILE)) == null || (availableGovernorsLine = Utils.fileReadOneLine(GOV_LIST_FILE)) == null) {
            prefSet.removePreference(mGovernorPref);

        } else {
            availableGovernors = availableGovernorsLine.split(" ");

            mGovernorPref.setEntryValues(availableGovernors);
            mGovernorPref.setEntries(availableGovernors);
            mGovernorPref.setValue(temp);
            mGovernorPref.setSummary(String.format(mGovernorFormat, temp));
            mGovernorPref.setOnPreferenceChangeListener(this);
        }

        // Disable the min/max list if we dont have a list file
        if (!Utils.fileExists(FREQ_LIST_FILE) || (availableFrequenciesLine = Utils.fileReadOneLine(FREQ_LIST_FILE)) == null) {
            mMinFrequencyPref.setEnabled(false);
            mMaxFrequencyPref.setEnabled(false);

        } else {
            availableFrequencies = availableFrequenciesLine.split(" ");

            frequencies = new String[availableFrequencies.length];
            for (int i = 0; i < frequencies.length; i++) {
                frequencies[i] = toMHz(availableFrequencies[i]);
            }

            // Min frequency
            if (!Utils.fileExists(FREQ_MIN_FILE) || (temp = Utils.fileReadOneLine(FREQ_MIN_FILE)) == null) {
                mMinFrequencyPref.setEnabled(false);

            } else {
                mMinFrequencyPref.setEntryValues(availableFrequencies);
                mMinFrequencyPref.setEntries(frequencies);
                mMinFrequencyPref.setValue(temp);
                mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(temp)));
                mMinFrequencyPref.setOnPreferenceChangeListener(this);
            }

            // Max frequency
            if (!Utils.fileExists(FREQ_MAX_FILE) || (temp = Utils.fileReadOneLine(FREQ_MAX_FILE)) == null) {
                mMaxFrequencyPref.setEnabled(false);

            } else {
                mMaxFrequencyPref.setEntryValues(availableFrequencies);
                mMaxFrequencyPref.setEntries(frequencies);
                mMaxFrequencyPref.setValue(temp);
                mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(temp)));
                mMaxFrequencyPref.setOnPreferenceChangeListener(this);
            }
        }

        // Cur frequency
        if (!Utils.fileExists(FREQ_CUR_FILE)) {
            FREQ_CUR_FILE = FREQINFO_CUR_FILE;
        }

        if (!Utils.fileExists(FREQ_CUR_FILE) || (temp = Utils.fileReadOneLine(FREQ_CUR_FILE)) == null) {
            mCurFrequencyPref.setEnabled(false);

        } else {
            mCurFrequencyPref.setSummary(toMHz(temp));

            mCurCPUThread.start();
        }
    }

    @Override
    public void onResume() {
        String temp;

        String availableIOSchedulersLine;
        int bropen, brclose;
        String currentIOScheduler;

        super.onResume();

        initFreqCapFiles();

        if (Utils.fileExists(IOSCHED_LIST_FILE) &&
            (availableIOSchedulersLine = Utils.fileReadOneLine(IOSCHED_LIST_FILE)) != null) {
            bropen = availableIOSchedulersLine.indexOf("[");
            brclose = availableIOSchedulersLine.lastIndexOf("]");
            if (bropen >= 0 && brclose >= 0) {
                currentIOScheduler = availableIOSchedulersLine.substring(bropen + 1, brclose);
                mIOSchedulerPref.setSummary(String.format(mIOSchedulerFormat, currentIOScheduler));
            }
        }

        if (Utils.fileExists(FREQ_MIN_FILE) && (temp = Utils.fileReadOneLine(FREQ_MIN_FILE)) != null) {
            mMinFrequencyPref.setValue(temp);
            mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat, toMHz(temp)));
        }

        if (Utils.fileExists(FREQ_MAX_FILE) && (temp = Utils.fileReadOneLine(FREQ_MAX_FILE)) != null) {
            mMaxFrequencyPref.setValue(temp);
            mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat, toMHz(temp)));
        }

        if (Utils.fileExists(GOV_FILE) && (temp = Utils.fileReadOneLine(GOV_FILE)) != null) {
            mGovernorPref.setSummary(String.format(mGovernorFormat, temp));
        }
    }

    public void setCurrentPerfProfileSummary() {
        String currentValue = SystemProperties.get(mPerfProfileProp);
        String summary = "";
        int count = mPerfProfileValues.length;
        for (int i = 0; i < count; i++) {
            try {
                if (mPerfProfileValues[i].compareTo(currentValue) == 0) {
                    summary = mPerfProfileEntries[i];
                }
            } catch (IndexOutOfBoundsException ex) {
                // Ignore
            }
        }
        mPerfProfilePref.setSummary(String.format(mPerfProfileFormat, summary));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCurCPUThread.interrupt();
        try {
            mCurCPUThread.join();
        } catch (InterruptedException e) {
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mUse16bppAlphaPref) {
            SystemProperties.set(USE_16BPP_ALPHA_PROP,
                    mUse16bppAlphaPref.isChecked() ? "1" : "0");
        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        if (preference == mPurgeableAssetsPref) {
            SystemProperties.set(PURGEABLE_ASSETS_PERSIST_PROP,
                    mPurgeableAssetsPref.isChecked() ? "1" : "0");

        if (preference == mKSMPref) {
            Utils.fileWriteOneLine(KSM_RUN_FILE, mKSMPref.isChecked() ? "1" : "0");
        }

        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        initFreqCapFiles();
        String fname = "";

        if (newValue != null) {
            if (preference == mIOSchedulerPref) {
                fname = IOSCHED_LIST_FILE;
            } else if (preference == mGovernorPref) {
                fname = GOV_FILE;
            } else if (preference == mMinFrequencyPref) {
                fname = FREQ_MIN_FILE;
            } else if (preference == mMaxFrequencyPref) {
                fname = FREQ_MAX_FILE;
            }

            if (Utils.fileWriteOneLine(fname, (String) newValue)) {
                if (preference == mIOSchedulerPref) {
                    mIOSchedulerPref.setSummary(String.format(mIOSchedulerFormat, (String) newValue));
                } else if (preference == mGovernorPref) {
                    mGovernorPref.setSummary(String.format(mGovernorFormat, (String) newValue));
                } else if (preference == mMinFrequencyPref) {
                    mMinFrequencyPref.setSummary(String.format(mMinFrequencyFormat,
                            toMHz((String) newValue)));
                } else if (preference == mMaxFrequencyPref) {
                    mMaxFrequencyPref.setSummary(String.format(mMaxFrequencyFormat,
                            toMHz((String) newValue)));
                }
                return true;
            } else {
                return false;
            }

            if (preference == mPerfProfilePref) {
                SystemProperties.set(mPerfProfileProp, String.valueOf(newValue));
                setCurrentPerfProfileSummary();
                return true;
            }
        }
        return false;
    }

    private String toMHz(String mhzString) {
        return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz")
                .toString();
    }
}
