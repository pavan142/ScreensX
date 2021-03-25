package com.frankenstein.screenx;

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
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.MutableLiveData;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;

import android.content.Intent;

import com.frankenstein.screenx.helper.Logger;
import com.frankenstein.screenx.helper.PermissionHelper;
import com.frankenstein.screenx.models.AppGroup;
import com.frankenstein.screenx.models.Screenshot;
import com.frankenstein.screenx.ui.adapters.HomePageAdapter;

import static com.frankenstein.screenx.Constants.PROGRESSBAR_TRANSITION;
import static com.frankenstein.screenx.helper.FileHelper.CUSTOM_SCREENSHOT_DIR;
import static com.frankenstein.screenx.helper.FileHelper.createIfNot;

public class MainActivity extends AppCompatActivity {

    private GridView _mGridView;
    private HomePageAdapter _adapter;
    private Logger _mLogger;
    private SwipeRefreshLayout _pullToRefresh;
    private View _mProgressBar;
    private View _mPermissionsDisplay;
    private View _mStoragePermissionsView;
    private View _mUsagePermissionsView;
    private View _mOverlayPermissionsView;

    private boolean _mPermissionsGranted = false;
    private Handler _mHandler;
    private boolean _mSortByDate = true;
    private boolean _mPaused = false;
    public Utils utils;

    private View _mHomePageContentLayout;
    private View _mHomePageContentEmpty;
    private View _mHomePageDisplayContent;

    private Toolbar _mToolbar;

    private MutableLiveData<HomePageState> _mState = new MutableLiveData<>();
    private HomePageState _mPrevState = HomePageState.REQUEST_PERMISSIONS;
    private enum HomePageState {
        REQUEST_PERMISSIONS,
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

        _mStoragePermissionsView = findViewById(R.id.storage_permissions);
        _mStoragePermissionsView.setOnClickListener(view -> goToStorageSettings());

        _mUsagePermissionsView = findViewById(R.id.usage_permissions);
        _mUsagePermissionsView.setOnClickListener(view -> goToUsageSettings());

        _mOverlayPermissionsView = findViewById(R.id.overlay_permissions);
        _mOverlayPermissionsView.setOnClickListener(view -> goToOverlaySettings());

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

    private void showRequestPermissionsScreen() {
        _mPermissionsGranted = false;
        _mPermissionsDisplay.setVisibility(View.VISIBLE);
        _mProgressBar.setVisibility(View.GONE);
        _mHomePageContentEmpty.setVisibility(View.GONE);
        checkPermissions();
    }

    private void showProgressBar() {
        _mPermissionsGranted = true;
        _mHomePageContentLayout.setAlpha(0);
        _mHomePageContentLayout.setVisibility(View.VISIBLE);
        _mPermissionsDisplay.setVisibility(View.GONE);
        _mProgressBar.setVisibility(View.VISIBLE);

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
        intent.setAction(ScreenXService.ACTION_ENABLE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void goToStorageSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void goToUsageSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void goToOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
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
        Utils.SortingCriterion criterion = (_mSortByDate) ? Utils.SortingCriterion.Date: Utils.SortingCriterion.Alphabetical;
        ArrayList<AppGroup> appgroups = ScreenXApplication.screenFactory.getAppGroups(criterion);
        if (appgroups.size() == 0) {
            _mLogger.log("appgroup size is zero");
          _mState.setValue(HomePageState.NO_CONTENT_SCREEN);
          return;
        }

        _mState.setValue(HomePageState.DISPLAY_CONTENT);

        for (AppGroup ag : appgroups)
            mascots.add(ag.mascot);
//        for (Screenshot s : mascots)
//            _mLogger.log(s.appName, s.name);
        _adapter = new HomePageAdapter(getApplicationContext(), mascots);
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

    public void checkPermissions() {
        boolean storagePermissions = PermissionHelper.hasStoragePermission(this);
        boolean usagePermissions = PermissionHelper.hasUsagePermission(this);
        boolean overlayPermissions = PermissionHelper.hasOverlayPermission(this);

        if (storagePermissions && usagePermissions && overlayPermissions) {
            _mLogger.log("Has all the permissions");
            _mState.setValue(HomePageState.LOADING_PROGRESS_BAR);
            return;
        }

        _mLogger.log("Permissions Missing:: storage ->", storagePermissions, "  usage ->", usagePermissions, "overlay ->", overlayPermissions);
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
        if (_mPaused)
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
