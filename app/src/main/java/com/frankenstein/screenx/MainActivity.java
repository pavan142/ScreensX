package com.frankenstein.screenx;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.Intent;

import com.frankenstein.screenx.helper.Logger;
import com.frankenstein.screenx.helper.PermissionHelper;
import com.frankenstein.screenx.models.AppGroup;
import com.frankenstein.screenx.models.Screenshot;
import com.frankenstein.screenx.ui.adapters.HomePageAdapter;
import com.frankenstein.screenx.ui.adapters.OnboardingPageAdapter;
import com.frankenstein.screenx.ui.adapters.PermissionPageAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import static com.frankenstein.screenx.Constants.PROGRESSBAR_TRANSITION;
import static com.frankenstein.screenx.helper.ArrayHelper.Same;
import static com.frankenstein.screenx.helper.FileHelper.CUSTOM_SCREENSHOT_DIR;
import static com.frankenstein.screenx.helper.FileHelper.createIfNot;
import static com.frankenstein.screenx.ui.adapters.PermissionPageAdapter.STORAGE_PERMISSION;
import static com.frankenstein.screenx.ui.adapters.PermissionPageAdapter.USAGE_PERMISSION;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_WRITE_STORAGE = 1;
    private GridView _mGridView;
    private HomePageAdapter _adapter;
    private Logger _mLogger;
    private SwipeRefreshLayout _pullToRefresh;
    private View _mProgressBar;
    private View _mPermissionsDisplay;
    private View _mOnboardingDisplay;
    private PermissionPageAdapter _mPermissionPageAdapter;
    private OnboardingPageAdapter _mOnboardingPageAdapter;
    private AlertDialog.Builder _mAlertBuilder;
    private Map<String, View> _mDisplayMap = new HashMap<>();

    private static final String PERMISSION_DISPLAY="PERMISSION_DISPLAY";
    private static final String ONBOARDING_DISPLAY="ONBOARDING_DISPLAY";
    private static final String PROGRESS_DISPLAY="PROGRESS_DISPLAY";
    private static final String HOMEPAGE_DISPLAY="HOMEPAGE_DISPLAY";

    private boolean _mPermissionsGranted = false;
    private Handler _mHandler;
    private boolean _mSortByDate = true;
    private boolean _mPaused = false;
    public Utils utils;

    private View _mHomePageContentLayout;
    private View _mHomePageContentEmpty;
    private View _mHomePageDisplayContent;

    private ArrayList<Screenshot> _mAppgroupMascots = new ArrayList<>();
    private ArrayList<Integer> _mAppgroupSizes = new ArrayList<>();

    private MutableLiveData<HomePageState> _mState = new MutableLiveData<>();
    private HomePageState _mPrevState = HomePageState.REQUEST_PERMISSIONS;
    private enum HomePageState {
        REQUEST_PERMISSIONS,
        ONBOARDING_SCREEN,
        LOADING_PROGRESS_BAR,
        NO_CONTENT_SCREEN,
        DISPLAY_CONTENT,
        PULL_REFRESH_PROGRESS
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homepage);

        utils = Utils.getInstance();
        utils.setContext(getApplicationContext());
        _mLogger = Logger.getInstance("MainActivity");
        _mLogger.log("-----ONCREATE------");
        _mHandler = new Handler(Looper.myLooper());
        _pullToRefresh = findViewById(R.id.pull_to_refresh);
        _pullToRefresh.setOnRefreshListener(() -> _mState.setValue(HomePageState.PULL_REFRESH_PROGRESS));

        _mGridView = findViewById(R.id.grid_view);
        _mHomePageContentLayout = findViewById(R.id.homepage_content_layout);
        _mHomePageContentEmpty = findViewById(R.id.homepage_content_empty);
        _mHomePageDisplayContent = findViewById(R.id.homepage_display_content);

        _mLogger.log("Adding observer on screenshots live data");
        ScreenXApplication.screenFactory.screenshots.observe(this, this::onScreenshotsChanged);
        _mProgressBar = findViewById(R.id.progress_bar);

        _mPermissionsDisplay = findViewById(R.id.permissions_display);
        _mOnboardingDisplay = findViewById(R.id.onboarding_display);

        _mDisplayMap.put(PERMISSION_DISPLAY, _mPermissionsDisplay);
        _mDisplayMap.put(ONBOARDING_DISPLAY, _mOnboardingDisplay);
        _mDisplayMap.put(PROGRESS_DISPLAY, _mProgressBar);
        _mDisplayMap.put(HOMEPAGE_DISPLAY, _mHomePageContentLayout);

        _mAlertBuilder = new AlertDialog.Builder(this);
        setupSearchBar();
        _mState.observeForever(this::onStateChange);
        _mState.setValue(HomePageState.REQUEST_PERMISSIONS);
    }

    private void setupSearchBar() {
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setCustomView(R.layout.toolbar_homepage);
        SearchView _mSearchView = findViewById(R.id.search_view);
        _mSearchView.findViewById(R.id.search_plate).setBackgroundColor(Color.TRANSPARENT);
        _mSearchView.setIconifiedByDefault(false);
        _mSearchView.setClickable(false);
        _mSearchView.setFocusableInTouchMode(false);

        View proxy = findViewById(R.id.search_proxy);
        proxy.setOnClickListener((View v) -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        });
    }

    private void onStateChange(HomePageState newState) {
        _mLogger.log("onStateChange", newState.toString());
        switch (newState) {
            case REQUEST_PERMISSIONS:
                showRequestPermissionsScreen();
                break;
            case ONBOARDING_SCREEN:
                showOnboardingScreen();
                break;
            case LOADING_PROGRESS_BAR:
                showProgressBar();
                break;
            case NO_CONTENT_SCREEN:
                showNoContentScreen();
                break;
            case DISPLAY_CONTENT:
                displayContent();
                break;
            case PULL_REFRESH_PROGRESS:
                refresh();
                break;
        }
        _mPrevState = newState;
    }

    private void bringDisplayIntoView(String displayName) {
        View display = _mDisplayMap.get(displayName);
        for (View v: _mDisplayMap.values()) {
            v.setVisibility(View.GONE);
        }
        display.setVisibility(View.VISIBLE);
    }

    private void showRequestPermissionsScreen() {
        _mPermissionsGranted = false;
        bringDisplayIntoView(PERMISSION_DISPLAY);
        checkPermissions();
    }

    private void showOnboardingScreen() {
        createOnboardingAdapter();
        bringDisplayIntoView(ONBOARDING_DISPLAY);
    }

    private void showProgressBar() {
        _mPermissionsGranted = true;
        bringDisplayIntoView(PROGRESS_DISPLAY);
        _mHomePageContentLayout.setAlpha(0);
        _mHomePageContentLayout.setVisibility(View.VISIBLE);
        _mLogger.log("Initializing Screenshots");
        createIfNot(CUSTOM_SCREENSHOT_DIR);
        refresh();
        _mLogger.log("Launching ScreenXService");
        startScreenXService();
    }


    private void transitionProgressBar() {
        _mProgressBar.animate().alpha(0).setDuration(PROGRESSBAR_TRANSITION);
        _mHomePageContentLayout.animate().alpha(1).setDuration(PROGRESSBAR_TRANSITION);
        _mHandler.postDelayed(() -> _mProgressBar.setVisibility(View.GONE), 1500);
    }

    private void showNoContentScreen() {
        if (_mPrevState == HomePageState.LOADING_PROGRESS_BAR)
            transitionProgressBar();
        _mLogger.log("Showing No Content Page");

        _mHomePageContentEmpty.setVisibility(View.VISIBLE);
        _mHomePageDisplayContent.setVisibility(View.GONE);

        _mLogger.log("current thread is", Thread.currentThread().toString());
    }

    private void displayContent() {
        if (_mPrevState == HomePageState.LOADING_PROGRESS_BAR)
            transitionProgressBar();
        _mLogger.log("Showing Content Page");
        _mHomePageDisplayContent.setVisibility(View.VISIBLE);
        _mHomePageContentEmpty.setVisibility(View.GONE);
    }

    private void startScreenXService() {
        Intent intent = new Intent(this, ScreenXService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void onPermissionRequested(String permissionName) {
        switch (permissionName) {
            case STORAGE_PERMISSION:
                goToStorageSettings();
                break;
            case USAGE_PERMISSION:
                goToUsageSettings();
                break;
        }
    }

    private void goToStorageSettings() {
        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
    }

    private void goToUsageSettings() {
        if (PermissionHelper.hasUsagePermission(this))
            return;
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            PackageManager pm = getPackageManager();
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent);
            } else {
                _mLogger.log("No Intent for usage access settings");
                showManualGrantOfUsageSettings();
            }
        } catch (ActivityNotFoundException e) {
            _mLogger.log("Got Activity Not Found Exception: Usage access settings", e.getMessage());
            showManualGrantOfUsageSettings();
        }
    }

    private void showManualGrantOfUsageSettings() {
        String appName = getResources().getString(R.string.app_name);
        String Message = "Go to Settings -> Search for Usage Access -> Look for " +
                appName + " -> Grant Permission to " + appName;
        _mAlertBuilder.setTitle("Usage Access Settings")
                .setMessage(Message)
                .setPositiveButton(getResources().getString(R.string.usage_settings_confirm), (dialog, which) -> {
                    Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                    startActivity(settingsIntent);
                })
                .show();
    }

    private void onScreenshotsChanged(ArrayList<Screenshot> screenshots) {
        _mLogger.log("received onScreenshotsChanged", Thread.currentThread().toString());
        if (_mState.getValue() == HomePageState.PULL_REFRESH_PROGRESS) {
            _pullToRefresh.setRefreshing(false);
        }
        attachAdapter();
    }

    private void refresh() {
        _mLogger.log("Refreshing Screenshots");
        ScreenXApplication.screenFactory.refresh(getApplicationContext());
    }

    public void attachAdapter() {
        _mLogger.log("attaching adapter");
        ArrayList<Screenshot> mascots = new ArrayList<>();
        ArrayList<Integer> appgroupSizes = new ArrayList<>();
        Utils.SortingCriterion criterion = (_mSortByDate) ? Utils.SortingCriterion.Date: Utils.SortingCriterion.Alphabetical;
        ArrayList<AppGroup> appgroups = ScreenXApplication.screenFactory.getAppGroups(criterion);
        if (appgroups.size() == 0) {
            _mLogger.log("appgroup size is zero");
          _mState.setValue(HomePageState.NO_CONTENT_SCREEN);
          return;
        }

        _mState.setValue(HomePageState.DISPLAY_CONTENT);

        for (AppGroup ag : appgroups) {
            mascots.add(ag.mascot);
            appgroupSizes.add(ag.screenshots.size());
        }

        if (Same(mascots, _mAppgroupMascots) && Same(appgroupSizes, _mAppgroupSizes))
            return;
        _mAppgroupMascots = mascots;
        _mAppgroupSizes = appgroupSizes;
        _adapter = new HomePageAdapter(getApplicationContext(), _mAppgroupMascots);
        _mGridView.setAdapter(_adapter);
        _mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Screenshot selected = mascots.get(i);
                _mLogger.log("The app group selected is", i, "the appName: ", selected.appName);
                Intent intent = new Intent(getBaseContext(), AppGroupActivity.class);
                intent.putExtra("APP_GROUP_NAME", selected.appName);
                startActivity(intent);
            }
        });
    }

    private void createPermissionAdapter() {
        if (_mPermissionPageAdapter != null)
            return;
        ViewPager2 permissionViewPager = findViewById(R.id.permission_view_pager);
        _mPermissionPageAdapter = new PermissionPageAdapter(this, this, this::onPermissionRequested);
        permissionViewPager.setAdapter(_mPermissionPageAdapter);
        TabLayout permissionTabLayout = findViewById(R.id.permission_tab_layout);
        new TabLayoutMediator(permissionTabLayout, permissionViewPager, (tab, pos) -> {}).attach();
    }

    private void createOnboardingAdapter() {
        if (_mOnboardingPageAdapter != null)
            return;
        ViewPager2 onboardingViewPager = findViewById(R.id.onboarding_view_pager);
        _mOnboardingPageAdapter = new OnboardingPageAdapter(this, this);
        onboardingViewPager.setAdapter(_mOnboardingPageAdapter);
        TabLayout permissionTabLayout = findViewById(R.id.onboarding_tab_layout);
        new TabLayoutMediator(permissionTabLayout, onboardingViewPager, (tab, pos) -> {}).attach();
    }

    public void checkPermissions() {
        boolean hasStoragePermission = PermissionHelper.hasStoragePermission(this);
        boolean hasUsagePermission = PermissionHelper.hasUsagePermission(this);

        if (hasStoragePermission && hasUsagePermission) {
            _mLogger.log("Has all the permissions");
            _mState.setValue(HomePageState.ONBOARDING_SCREEN);
//            _mState.setValue(HomePageState.LOADING_PROGRESS_BAR);
            return;
        }

        createPermissionAdapter();

        _mPermissionPageAdapter.onPermissionChanged(STORAGE_PERMISSION, hasStoragePermission);
        _mPermissionPageAdapter.onPermissionChanged(USAGE_PERMISSION, hasUsagePermission);

        _mLogger.log("Permissions Missing:: storage ->", hasStoragePermission, "  usage ->", hasUsagePermission);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: This can be clubbed along with onResume itself
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !_mPermissionsGranted) {
            checkPermissions();
        }
    }

    @Override
    protected void onResume() {
        _mLogger.log("onResume", _mState.getValue().compareTo(HomePageState.LOADING_PROGRESS_BAR) > 0);
        if (_mPaused && _mState.getValue() == HomePageState.DISPLAY_CONTENT)
            refresh();
        super.onResume();

        // This step is to reinitialize the floating touch bar, once it is closed
        // TODO: Start the service with that specific intent itself, instead of generic intent
        if (_mPaused && _mState.getValue() == HomePageState.DISPLAY_CONTENT)
            startScreenXService();
        _mPaused = false;
    }

    @Override
    protected void onPause() {
        _mLogger.log("onPause");
        _mPaused = true;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
