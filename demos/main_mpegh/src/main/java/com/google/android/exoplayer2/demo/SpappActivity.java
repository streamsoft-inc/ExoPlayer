package com.google.android.exoplayer2.demo;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sony.immersive_audio.sal.SiaCpInfo;
import com.sony.immersive_audio.sal.SiaOptimizationInfo;
import com.sony.immersive_audio.sal.SiaOptimizationMode;
import com.sony.immersive_audio.sal.SiaResult;
import com.sony.immersive_audio.sal.SiaServerAccess;
import com.sony.immersive_audio.sal.SiaServerAccessListener;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// SAL impl: import SAL module

// SpappActivity wraps SAL library and optimization sequences
public class SpappActivity extends AppCompatActivity {

    private static final String TAG = "SpappActivity";
    private SiaServerAccess mSal;
    protected TextView textOutput;
    protected ProgressBar progressBar;

    /**
     * A view controller class managing Cp device list view.
     */
    protected SalDeviceListViewController deviceListViewController;

    /**
     * A menu controller class managing menu selections.
     */
    private MenuController menuController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // bind SAL service
        final Intent intent = new Intent(this, SiaServerAccess.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // create SAL Cp list view controllers
        deviceListViewController = new SalDeviceListViewController(this);
    }

    void buildSalUi()
    {
        // demo app UIs
        textOutput = findViewById(R.id.textView); // added text view
        textOutput.setMovementMethod(ScrollingMovementMethod.getInstance());
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(android.widget.ProgressBar.INVISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // unbind SAL service
        if (mSal != null) {
            mSal.unregisterListener(mSiaServerAccessListener);
            addOutput("unbindService()");
            mSal = null;
        }
        unbindService(mServiceConnection);
    }

    ///////////////////////////////////////////////////////////////////
    // SAL optimization sequence

    /**
     *  Server connection class.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // start optimization
            final SiaServerAccess.LocalBinder binder = (SiaServerAccess.LocalBinder) service;
            mSal = binder.getService();
            mSal.registerListener(mSiaServerAccessListener);

            onSalServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSal = null;
        }
    };

    /**
     * A callback function on SAL is connected.
     */
    private void onSalServiceConnected() {
        // update device list
        deviceListViewController.setSal(mSal);
        deviceListViewController.updateOptimizationMode();
        deviceListViewController.updateView();

        startOptimize();

        // update progressbar
        progressBar.setMax(100);
        progressBar.setVisibility(android.widget.ProgressBar.VISIBLE);
    }

    /**
     * Start optimization
     */
    private void startOptimize() {
        final Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        final Uri uri = intent.getData();
        if (uri == null) {
            return;
        }

        // URL format:
        // scheme://host/path?query
        // scheme: [SP identifier], host/path: [SP app identifier], query: [optimization_info]
        // Optimization info format:
        // hrtf=[hrtfOneTimeURL]&cp=[cpOneTimeUrl]&
        // app=[appName]&dev=[deviceName]&devtype=[deviceType]
        // * SAL should get entire optimization_info as string type. *

        // extract optimization info from received URL.
        final String optimizationInfo = uri.getEncodedQuery();
        // start optimize
        final SiaResult ret = mSal.startOptimize(optimizationInfo);
        addOutput("----------");
        addOutput("startOptimize() " + ret);
    }

    /**
     *  A listener class managing server access sequences.
     */
    private SiaServerAccessListener mSiaServerAccessListener = new SiaServerAccessListener() {
        @Override
        public void onOptimizationStarted(SiaOptimizationInfo info) {
            Log.d(TAG, "onOptimizationStarted");

            addOutput("onOptimizationStarted");
            addOutput("  app=" + info.getAppName());
            addOutput("  dev=" + info.getDeviceName());
            addOutput("  devtype=" + info.getDeviceType());
            addOutput("  hrtfState=" + info.getHrtfOptimizationState());
            addOutput("  cpState=" + info.getCpOptimizationState());
            progressBar.setVisibility(android.widget.ProgressBar.VISIBLE);
        }

        @Override
        public void onOptimizationProgress(int percent) {
            Log.d(TAG, "onOptimizationProgress" + percent);

            // update progressbar
            progressBar.setProgress(percent);
            // addOutput("onOptimizationProgress " + percent);
        }

        @Override
        public void onOptimizationCompleted(SiaResult result) {
            Log.d(TAG, "onOptimizationCompleted " + result);

            // update progressbar
            progressBar.setVisibility(android.widget.ProgressBar.INVISIBLE);
            addOutput("onOptimizationCompleted " + result);
            Toast.makeText(getApplicationContext(), "Optimization completed", Toast.LENGTH_LONG)
                    .show();

            deviceListViewController.updateView();
        }

        @Override
        public void onOptimizationStateChanged(SiaOptimizationInfo info) {
            // receives optimization state change
            addOutput("onOptimizationStateChagned");
            addOutput("  app=" + info.getAppName());
            addOutput("  dev=" + info.getDeviceName());
            addOutput("  devtype=" + info.getDeviceType());
            addOutput("  hrtfState=" + info.getHrtfOptimizationState());
            addOutput("  cpState=" + info.getCpOptimizationState());

            deviceListViewController.updateView();
        }
    };

    /**
     *  A callback function called on optimization canceled.
     */
    private void onButtonCancel() {
        SiaResult ret = mSal.cancelOptimize();
        addOutput("cancelOptimize " + ret);
    }

    /**
     *  get optimization info
     */
    private void getOptimizationInfo() {
        final SiaOptimizationInfo info = mSal.getOptimizationInfo();
        addOutput("getOptimizationInfo");
        addOutput("  app=" + info.getAppName());
        addOutput("  dev=" + info.getDeviceName());
        addOutput("  devtype=" + info.getDeviceType());
        addOutput("  hrtfState=" + info.getHrtfOptimizationState());
        addOutput("  cpState=" + info.getCpOptimizationState());
    }


    /**
     *  Add outpu text
     */
    protected void addOutput(String s) {
        Log.d(TAG, s);
        String current = textOutput.getText().toString();
        current += s;
        current += "\n";
        textOutput.setText(current);
    }

    /**
     * A view controller class managing Cp device list view.
     */
    public class SalDeviceListViewController {
        private String TAG = "DeviceListViewController";

        /**
         *  A context class called this controller.
         */
        private Context context;

        /**
         *  SAL instance
         */
        private  SiaServerAccess sal;

        /**
         *  HRTF optimization enable switch
         */
        private Switch switchHrtf;

        /**
         *  CP optimization enable switch
         */
        private Switch switchCp;

        /**
         *  A list view object obrained from root view.
         */
        private ListView deviceListView;

        /**
         *  A list content adapter
         */
        private CpAdapter deviceListAdaper;

        /**
         *  Dialog object on delete cp file.
         */
        private AlertDialog deleteComfirmDialog;

        /**
         *  Constructor.
         */
        SalDeviceListViewController(Context context) {
            this.context = context;
        }

        /**
         *  Set root view instance including a Cp list view.
         */
        public void setRootView(View rootView) {
            if (rootView == null) {
                Log.w(TAG, "setRootView: rootView is null.");
                return;
            }
            buildUi(rootView);
        }

        /**
         *  Set SAL instance.
         */
        public void setSal(SiaServerAccess sal) {
            this.sal = sal;
        }

        /**
         *  Construct UI view references.
         */
        private void buildUi(View rootView) {
            // optimization switches
            switchHrtf = rootView.findViewById(R.id.personal_opt_switch);
            switchHrtf.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged (CompoundButton buttonView,
                                              boolean isChecked) {
                    if (sal == null) {
                        Log.w(TAG, "SwitchHrtf onCheckedChanged: SAL not connected.");
                        return;
                    }
                    sal.setHrtfOptimizationMode(
                            switchHrtf.isChecked() ? SiaOptimizationMode.ON : SiaOptimizationMode.OFF);
                    updateView();
                }
            });

            switchCp = rootView.findViewById(R.id.headphone_opt_switch);
            switchCp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged (CompoundButton buttonView,
                                              boolean isChecked) {
                    if (sal == null) {
                        Log.w(TAG, "SwitchCp onCheckedChanged: SAL not connected.");
                        return;
                    }
                    sal.setCpOptimizationMode(
                            switchCp.isChecked() ? SiaOptimizationMode.ON : SiaOptimizationMode.OFF);
                    updateView();
                }
            });

            // device list views
            deviceListView = rootView.findViewById(R.id.list_cp);
            deviceListAdaper = new CpAdapter(context);
            deviceListView.setAdapter(deviceListAdaper);
            deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                    if (sal == null) {
                        Log.w(TAG, "DeviceListView onItemClick: SAL is not connected.");
                        return;
                    }
                    final SiaCpInfo info = (SiaCpInfo) deviceListAdaper.getItem(position);
                    sal.setPreferredCp(info.getDeviceName());
                    updateCpList();
                }
            });

            // clear preferred button
            rootView.findViewById(R.id.button_clear_preferred_cp).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clearPreferredCp();
                }
            });

            updateView();
        }

        /**
         *  Notify CP list is updated.
         */
        void updateView() {
            if (sal != null) {
                if (switchHrtf != null) {
                    switchHrtf.setChecked(sal.getHrtfOptimizationMode() == SiaOptimizationMode.ON);
                }
                if (switchCp != null) {
                    switchCp.setChecked(sal.getCpOptimizationMode() == SiaOptimizationMode.ON);
                }
            }
            updateCpList();

        }

        /**
         *  Update CP list.
         */
        private void updateCpList() {
            if (sal == null) {
                Log.w(TAG, "updateCpList: SAL is not connected.");
                return;
            }
            if (deviceListAdaper == null) {
                Log.w(TAG, "updateCpList: DeviceListAdaper is not created.");
                return;
            }
            deviceListAdaper.setCpList(sal.getCpList());

            if (sal.getCpOptimizationMode()==SiaOptimizationMode.OFF) {
                deviceListView.setVisibility(View.INVISIBLE);
            }
            else {
                deviceListView.setVisibility(View.VISIBLE);
            }
        }

        /**
         *  Clear all preferred passive devices.
         */
        private void clearPreferredCp() {
            if (sal == null) {
                Log.w(TAG, "clearPreferredCp: SAL is not connected.");
                return;
            }
            sal.setPreferredCp(null);
            updateCpList();
        }

        /**
         *  Delete a Cp device file.
         */
        private void deleteCp(final String deviceName) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            deleteComfirmDialog = builder.setMessage("delete " + deviceName + "?").
                    setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            if (sal != null) {
                                Log.w(TAG, "DeleteComfirmDialog OK: SAL is not connected.");
                                sal.deleteCp(deviceName);
                            }
                            updateCpList();
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            }).create();
            deleteComfirmDialog.show();

        }

        /**
         *  List adapter class for Cp device list.
         */
        private class CpAdapter extends BaseAdapter {
            private Context mContext;
            private List<SiaCpInfo> mItems;

            CpAdapter(Context context) {
                mContext = context;
            }

            void setCpList(List<SiaCpInfo> items) {
                mItems = items;
                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                if (mItems == null) {
                    return 0;
                }
                return mItems.size();
            }

            @Override
            public Object getItem(int i) {
                if (mItems == null) {
                    return null;
                }
                return mItems.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (sal == null) {
                    Log.w(TAG, "CpAdapter getView: SAL is not connected.");
                    return view;
                }

                if (view == null) {
                    LayoutInflater inflater = LayoutInflater.from(mContext);
                    view = inflater.inflate(R.layout.listitem_cp, viewGroup, false);
                }

                final SiaCpInfo info = mItems.get(i);

                final TextView textDeviceName = view.findViewById(R.id.text_device_name);
                textDeviceName.setText(info.getDeviceName());

                final TextView textDeviceType = view.findViewById(R.id.text_device_type);
                textDeviceType.setText(info.getDeviceType().toString());

                final TextView textDate = view.findViewById(R.id.text_date);
                if (info.getDate() != null) {
                    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
                    sdf.setTimeZone(TimeZone.getDefault());
                    textDate.setText(sdf.format(info.getDate()));
                }

                final TextView textPreferred = view.findViewById(R.id.text_preferred);
                final String preferred = sal.getPreferredCp();
                if (preferred != null && TextUtils.equals(preferred, info.getDeviceName())) {
                    textPreferred.setText("Preferred CP");
                } else {
                    textPreferred.setText("");
                }

                final TextView textCurrent = view.findViewById(R.id.text_current);
                final SiaCpInfo current = sal.getCurrentCp();
                if (current != null
                        && TextUtils.equals(current.getDeviceName(), info.getDeviceName())) {
                    textCurrent.setText("Current CP");
                } else {
                    textCurrent.setText("");
                }

                final Button buttonDelete = view.findViewById(R.id.button_delete);
                buttonDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        deleteCp(info.getDeviceName());
                    }
                });

                if (current != null
                        && TextUtils.equals(current.getDeviceName(), info.getDeviceName())) {
                    buttonDelete.setVisibility(View.INVISIBLE);
                } else {
                    buttonDelete.setVisibility(View.VISIBLE);
                }


                if (info.isSelectable()) {
                    view.setBackgroundColor(Color.rgb(255, 255, 255));
                } else {
                    view.setBackgroundColor(Color.rgb(224, 224, 224));
                }

                return view;
            }
        }

        /**
         *  Update optimization modes.
         */
        void updateOptimizationMode() {
            if (sal == null) {
                Log.w(TAG, "updateOptimizationMode: SAL not connected.");
                return;
            }
            if (switchHrtf == null) {
                Log.w(TAG, "updateOptimizationMode: SwitchHrtf is not obtained.");
                return;
            }
            if (switchCp == null) {
                Log.w(TAG, "updateOptimizationMode: SwitchCp is not obtained.");
                return;
            }
            switchHrtf.setChecked(sal.getHrtfOptimizationMode() == SiaOptimizationMode.ON);
            switchCp.setChecked(sal.getCpOptimizationMode() == SiaOptimizationMode.ON);
        }
    }

    //-------------------------------------------------------------------------//
    // menu handlers
    //-------------------------------------------------------------------------//

    /**
     * MenuController: A class managing menu items
     */
    public class MenuController {
        private String TAG = "MenuController";

        /**
         *  An activity class called this controller.
         */
        private Context context;

        // hrtf/cp file lists

        /**
         * An alert dialog instance to show the list of coefficient files on local storage.
         */
        private AlertDialog alertDialog;

        /**
         * SAL related 360RA setting dialog.
         */
        private AlertDialog iaSettingDialog;

        View iaSettingView;

        /**
         * Constructor
         * @param context
         */
        MenuController(Context context) {
            this.context = context;

            // create 360RA Setting view
            final LayoutInflater inflater = LayoutInflater.from(context);
            iaSettingView = inflater.inflate(R.layout.ia_setting_dialog, null);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            iaSettingDialog = builder.setTitle("360RA Setting")
                    .setView(iaSettingView)
                    .create();

        }

        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_item_version_display:
                    showVersionDialog();
                    return true;
                case R.id.menu_ia_setting:
                    showIASettingDialog();
            }
            return true;
        }
        /**
         * Show version on alert dialog
         */
        private void showVersionDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.application_name).setMessage("Version " + BuildConfig.VERSION_NAME);
            alertDialog = builder.show();
        }

        /**
         * Show IA setting dialog.
         */
        private void showIASettingDialog() {
            iaSettingDialog.show();
        }
    }


    /**
     * Create and show menu items.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sample_chooser_menu, menu);

        menuController = new MenuController(this);
        if (deviceListViewController != null) {
            deviceListViewController.setRootView(menuController.iaSettingView);
        }
        return true;
    }

    /**
     * A callback on selected menu items.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        menuController.onOptionsItemSelected(item);
        return super.onOptionsItemSelected(item);
    }
}
